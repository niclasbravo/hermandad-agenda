package cl.udd.hermandad.agenda.confirmaciones.aplicacion;

import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.citas.dominio.EstadoCita;
import cl.udd.hermandad.agenda.citas.dominio.ICitaRepository;
import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.confirmaciones.dominio.*;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** CU-05, RF-19 a RF-21, RF-33, RF-38, RF-39. */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    public record Detalle(List<Mensaje> mensajes, List<RespuestaPaciente> respuestas, List<LlamadaConfirmacion> llamadas) {
    }

    private final IMensajeRepository mensajes;
    private final ICitaRepository citas;
    private final IServicioMensajeria mensajeria;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;
    private final Reloj reloj;
    /** Conteo de intentos fallidos por mensaje (en memoria; sin columnas nuevas). */
    private final Map<Long, Integer> intentos = new ConcurrentHashMap<>();
    /** Respuestas entrantes ya procesadas (idempotencia del webhook). */
    private final Cache<String, Boolean> entrantesProcesados = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(2)).maximumSize(100_000).build();

    public NotificacionService(IMensajeRepository mensajes, ICitaRepository citas, IServicioMensajeria mensajeria,
                               ParametroService parametros, AuditoriaService auditoria, ApplicationEventPublisher eventos,
                               PlatformTransactionManager tm, Reloj reloj) {
        this.mensajes = mensajes;
        this.citas = citas;
        this.mensajeria = mensajeria;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.transaccion = new TransactionTemplate(tm);
        this.reloj = reloj;
    }

    // ---------------- Programación (sección 11.1), dentro de la transacción de la reserva ----------------

    @Transactional(propagation = Propagation.MANDATORY)
    public void programarConfirmacion(Cita cita) {
        String nombre = cita.getEstado() == EstadoCita.PENDIENTE_VERIFICACION
                ? PlantillaMensaje.CONFIRMACION_PENDIENTE : PlantillaMensaje.CONFIRMACION;
        programar(cita, nombre, reloj.ahora(), EnviadoPor.SISTEMA, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void programarRecordatorios(Cita cita) {
        LocalDateTime ahora = reloj.ahora();
        ReglaRecordatorio regla = new ReglaRecordatorio(parametros.recordatorioHora(), parametros.refuerzosHorasAntes());
        LocalDateTime inicio = cita.getCupo().inicio();
        regla.momentoRecordatorio(inicio, ahora)
                .ifPresent(m -> programar(cita, PlantillaMensaje.RECORDATORIO, m, EnviadoPor.SISTEMA, null));
        for (LocalDateTime m : regla.momentosRefuerzo(inicio, ahora)) {
            programar(cita, PlantillaMensaje.REFUERZO, m, EnviadoPor.SISTEMA, null);
        }
    }

    /** Envía la plantilla ANULACION a todos los destinatarios. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enviarAnulacion(Cita cita, boolean porElPoliclinico) {
        programar(cita, porElPoliclinico ? PlantillaMensaje.ANULACION_POLICLINICO : PlantillaMensaje.ANULACION,
                reloj.ahora(), EnviadoPor.SISTEMA, null);
    }

    /** CU-05 A2: al anular, los mensajes programados de la cita no salen. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelarProgramados(Cita cita) {
        int n = 0;
        for (Mensaje m : mensajes.programadosDeCita(cita.getId())) {
            if (m.getPlantilla().getTipo() != TipoPlantilla.ANULACION) {
                cancelar(m);
                n++;
            }
        }
        return n;
    }

    private void programar(Cita cita, String plantilla, LocalDateTime momento, EnviadoPor por, Integer idUsuario) {
        PlantillaMensaje p = mensajes.plantilla(plantilla)
                .orElseThrow(() -> new IllegalStateException("Falta la plantilla " + plantilla));
        for (Paciente.TelefonoMensajeria t : cita.getPaciente().telefonosMensajeria()) {
            mensajes.guardar(new Mensaje(cita, p, t.destinatario(), t.telefono(), por, idUsuario, momento));
        }
    }

    private void cancelar(Mensaje m) {
        m.cancelar(reloj.ahora());
        mensajes.guardar(m);
        auditoria.registrarComo(null, AccionAuditoria.MODIFICAR, "mensaje", m.getId(), "cancelado: cita anulada");
    }

    // ---------------- Envío (sección 11.2) ----------------

    /** Lo llama el programador cada minuto. Cada mensaje se procesa en su propia transacción. */
    public int enviarPendientes() {
        List<Long> ids = mensajes.pendientes(reloj.ahora(), 200);
        int enviados = 0;
        for (Long id : ids) {
            try {
                Boolean ok = transaccion.execute(s -> enviarUno(id));
                if (Boolean.TRUE.equals(ok)) {
                    enviados++;
                }
            } catch (Exception e) {
                log.error("Error al procesar el mensaje {}", id, e);
            }
        }
        return enviados;
    }

    private boolean enviarUno(Long idMensaje) {
        Mensaje m = mensajes.buscar(idMensaje).orElse(null);
        if (m == null || !m.estaProgramado()) {
            return false;
        }
        Cita cita = m.getCita();
        TipoPlantilla tipo = m.getPlantilla().getTipo();
        if (tipo != TipoPlantilla.ANULACION && !cita.estaVigente()) {
            cancelar(m);
            return false;
        }
        LocalDateTime ahora = reloj.ahora();
        try {
            String idExterno = mensajeria.enviarPlantilla(m.getTelefonoDestino(), m.getPlantilla().getNombreProveedor(),
                    RedactorMensajes.parametros(m.getPlantilla(), cita), m.getPlantilla().llevaBotones() ? RedactorMensajes.BOTONES : List.of());
            m.marcarEnviado(idExterno, ahora);
            intentos.remove(idMensaje);
            mensajes.guardar(m);
            switch (tipo) {
                case CONFIRMACION -> cita.marcarConfirmacionEnviada();
                case RECORDATORIO, REFUERZO -> cita.marcarSinRespuesta();
                default -> { }
            }
            citas.guardar(cita);
            return true;
        } catch (RuntimeException e) {
            int n = intentos.merge(idMensaje, 1, Integer::sum);
            log.warn("Falló el envío del mensaje {} (intento {}): {}", idMensaje, n, e.getMessage());
            if (n >= parametros.mensajeReintentos()) {
                intentos.remove(idMensaje);
                m.marcarFallido(ahora);
                mensajes.guardar(m);
                if (tipo == TipoPlantilla.CONFIRMACION && todasFallidas(cita, TipoPlantilla.CONFIRMACION)) {
                    cita.marcarConfirmacionNoEnviada();
                    citas.guardar(cita);
                }
                auditoria.registrarComo(null, AccionAuditoria.MODIFICAR, "mensaje", m.getId(),
                        "No enviado tras " + n + " intentos");
            }
            return false;
        }
    }

    /** D-07: la cita queda «no enviada» si ningún destinatario recibió la confirmación. */
    private boolean todasFallidas(Cita cita, TipoPlantilla tipo) {
        return mensajes.deCita(cita.getId()).stream()
                .filter(x -> x.getPlantilla().getTipo() == tipo)
                .allMatch(x -> x.getEstadoEntrega() == EstadoEntrega.FALLIDO);
    }

    // ---------------- Avisos y respuestas (sección 11.3) ----------------

    /** Avisos de estado del proveedor. Idempotente: los avisos repetidos o atrasados no cambian nada. */
    @Transactional
    public boolean procesarEstado(String idExterno, EstadoEntrega estado) {
        Optional<Mensaje> encontrado = mensajes.buscarPorIdExterno(idExterno);
        if (encontrado.isEmpty()) {
            return false;
        }
        Mensaje m = encontrado.get();
        boolean cambio = m.actualizarEstado(estado, reloj.ahora());
        if (!cambio) {
            return false;
        }
        mensajes.guardar(m);
        if (estado == EstadoEntrega.FALLIDO && m.getPlantilla().getTipo() == TipoPlantilla.CONFIRMACION
                && todasFallidas(m.getCita(), TipoPlantilla.CONFIRMACION)) {
            Cita cita = m.getCita();
            cita.marcarConfirmacionNoEnviada();
            citas.guardar(cita);
        }
        return true;
    }

    /** Respuesta del paciente (webhook). {@code idEntrante} identifica la respuesta para ignorar repetidos. */
    @Transactional
    public boolean procesarRespuesta(String idExternoOriginal, Respuesta respuesta, String idEntrante) {
        if (idEntrante != null && entrantesProcesados.getIfPresent(idEntrante) != null) {
            return false;
        }
        Optional<Mensaje> m = mensajes.buscarPorIdExterno(idExternoOriginal);
        if (m.isEmpty()) {
            return false;
        }
        aplicarRespuesta(m.get(), respuesta);
        if (idEntrante != null) {
            entrantesProcesados.put(idEntrante, Boolean.TRUE);
        }
        return true;
    }

    /** Mismo procesamiento, por id de mensaje (lo usa el simulador). */
    @Transactional
    public void procesarRespuesta(Long idMensaje, Respuesta respuesta) {
        Mensaje m = mensajes.buscar(idMensaje).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese mensaje."));
        aplicarRespuesta(m, respuesta);
    }

    private void aplicarRespuesta(Mensaje m, Respuesta respuesta) {
        mensajes.guardarRespuesta(new RespuestaPaciente(m, respuesta, reloj.ahora()));
        Cita cita = m.getCita();
        if (respuesta == Respuesta.ASISTIRA) {
            cita.confirmar();
            citas.guardar(cita);
        } else if (cita.estaVigente()) {
            eventos.publishEvent(new SolicitudAnulacion(cita.getId(), OrigenAnulacion.PACIENTE_MENSAJE,
                    "Respondió «No podré asistir» por WhatsApp", null));
        }
    }

    // ---------------- Acciones de la TENS/TONS (RF-38, RF-39) ----------------

    @Transactional
    public List<Mensaje> reenviarRecordatorio(Long idCita) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        Cita cita = cita(idCita);
        if (!cita.estaVigente()) {
            throw ErrorNegocio.regla("CITA_NO_VIGENTE", "Esta cita ya no está vigente.");
        }
        programar(cita, PlantillaMensaje.RECORDATORIO, reloj.ahora(), EnviadoPor.TENS, idUsuario);
        auditoria.registrar(AccionAuditoria.CREAR, "mensaje", idCita, "Reenvío manual del recordatorio");
        return mensajes.deCita(idCita);
    }

    @Transactional
    public LlamadaConfirmacion registrarLlamada(Long idCita, ResultadoLlamada resultado, String comentario) {
        if (resultado == null) {
            throw ErrorNegocio.validacion("resultado", "Elige el resultado de la llamada.");
        }
        Integer idUsuario = SesionActual.usuario().idUsuario();
        Cita cita = cita(idCita);
        if (!cita.estaVigente()) {
            throw ErrorNegocio.regla("CITA_NO_VIGENTE", "Esta cita ya no está vigente.");
        }
        LlamadaConfirmacion l = mensajes.guardarLlamada(new LlamadaConfirmacion(cita, idUsuario, resultado, comentario, reloj.ahora()));
        auditoria.registrar(AccionAuditoria.CREAR, "llamada_confirmacion", l.getId(), "Cita " + idCita + ": " + resultado);
        switch (resultado) {
            case CONFIRMO -> {
                cita.confirmarPorTelefono();
                citas.guardar(cita);
            }
            case NO_ASISTIRA -> eventos.publishEvent(new SolicitudAnulacion(idCita, OrigenAnulacion.TENS,
                    comentario == null || comentario.isBlank() ? "Avisó por teléfono que no asistirá" : comentario, idUsuario));
            case NO_CONTESTO -> { }
        }
        return l;
    }

    @Transactional(readOnly = true)
    public Detalle detalle(Long idCita) {
        return new Detalle(mensajes.deCita(idCita), mensajes.respuestasDeCita(idCita), mensajes.llamadasDeCita(idCita));
    }

    @Transactional(readOnly = true)
    public List<Mensaje> enviadosA(String telefono) {
        return mensajes.enviadosA(telefono, 50);
    }

    @Transactional(readOnly = true)
    public List<String> telefonosConMensajes() {
        return mensajes.telefonosConMensajes();
    }

    /** Solo demo: adelanta el recordatorio programado de una cita para enviarlo ahora. */
    @Transactional
    public void forzarRecordatorio(Long idCita) {
        Cita cita = cita(idCita);
        if (!cita.estaVigente()) {
            throw ErrorNegocio.regla("CITA_NO_VIGENTE", "Esta cita ya no está vigente.");
        }
        List<Mensaje> programados = mensajes.programadosDeCita(idCita).stream()
                .filter(m -> m.getPlantilla().getTipo() == TipoPlantilla.RECORDATORIO).toList();
        programados.forEach(this::cancelarSinAuditar);
        programar(cita, PlantillaMensaje.RECORDATORIO, reloj.ahora(), EnviadoPor.SISTEMA, null);
    }

    private void cancelarSinAuditar(Mensaje m) {
        m.cancelar(reloj.ahora());
        mensajes.guardar(m);
    }

    private Cita cita(Long idCita) {
        return citas.buscar(idCita).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos esa cita."));
    }
}
