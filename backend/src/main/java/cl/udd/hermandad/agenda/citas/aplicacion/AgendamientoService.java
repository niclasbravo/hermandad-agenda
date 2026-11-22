package cl.udd.hermandad.agenda.citas.aplicacion;

import cl.udd.hermandad.agenda.agenda.aplicacion.AgendaService;
import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.agenda.dominio.*;
import cl.udd.hermandad.agenda.citas.dominio.*;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.compartido.Textos;
import cl.udd.hermandad.agenda.compartido.TraductorErroresBaseDatos;
import cl.udd.hermandad.agenda.compartido.UsuarioSesion;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.SolicitudAnulacion;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.VerificacionRegistrada;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import cl.udd.hermandad.agenda.pacientes.dominio.ResultadoVerificacion;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * CU-04 y la reserva de CU-08. Solo conoce las interfaces ICitaRepository e ICupoRepository (patrón Repository).
 */
@Service
@Transactional
public class AgendamientoService {

    public record FechaSerie(LocalDate fecha, LocalTime hora, Long idCupo, String motivo) {
    }

    public record PrevisualizacionSerie(List<FechaSerie> conCupo, List<FechaSerie> sinCupo) {
    }

    private final ICitaRepository citas;
    private final ICupoRepository cupos;
    private final PacienteService pacientes;
    private final NotificacionService notificaciones;
    private final ListaEsperaService listaEspera;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Reloj reloj;

    public AgendamientoService(ICitaRepository citas, ICupoRepository cupos, PacienteService pacientes,
                               NotificacionService notificaciones, ListaEsperaService listaEspera,
                               ParametroService parametros, AuditoriaService auditoria, Reloj reloj) {
        this.citas = citas;
        this.cupos = cupos;
        this.pacientes = pacientes;
        this.notificaciones = notificaciones;
        this.listaEspera = listaEspera;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // ---------------- Consulta (RF-14) ----------------

    @Transactional(readOnly = true)
    public List<CupoVista> consultarCupos(FiltroCupos filtro) {
        LocalDateTime ahora = reloj.ahora();
        List<Cupo> lista = filtro.soloWeb() ? cupos.buscarDisponiblesWeb(filtro, ahora) : cupos.buscarDisponibles(filtro, ahora);
        return lista.stream().map(c -> AgendaService.vista(c, false, null, ahora)).toList();
    }

    // ---------------- Reserva por la TENS/TONS (RF-15) ----------------

    public Cita reservar(Integer idPaciente, Long idCupo) {
        UsuarioSesion u = SesionActual.usuario();
        return reservarInterno(idPaciente, idCupo, CanalReserva.TENS, u.idUsuario(), null);
    }

    // ---------------- Reserva en línea (RF-34, sección 10.4) ----------------

    public Cita reservarEnLinea(Integer idPaciente, Long idCupo) {
        return reservarInterno(idPaciente, idCupo, CanalReserva.EN_LINEA, null, null);
    }

    private Cita reservarInterno(Integer idPaciente, Long idCupo, CanalReserva canal, Integer idUsuario, SerieSesiones serie) {
        LocalDateTime ahora = reloj.ahora();
        // 1. SELECT … FOR UPDATE del cupo.
        Cupo cupo = cupos.obtenerParaReserva(idCupo)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese horario."));
        Paciente paciente = pacientes.obtener(idPaciente);
        boolean habilitado = paciente.estaHabilitado();
        // 2. RN-01: la TENS/TONS solo reserva a pacientes habilitados (422).
        if (canal == CanalReserva.TENS && !habilitado) {
            throw ErrorNegocio.regla("PACIENTE_NO_HABILITADO", TraductorErroresBaseDatos.MSJ_NO_HABILITADO);
        }
        // 3. RN-02: el cupo debe estar disponible (409). En la serie se aceptan cupos aún no publicados.
        boolean reservado = cupos.tieneCitaVigente(idCupo);
        EstadoCupo estado = cupo.estado(ahora, reservado);
        boolean disponible = cupo.getBloque().isActivo() && (estado == EstadoCupo.DISPONIBLE
                || (serie != null && estado == EstadoCupo.NO_PUBLICADO && cupo.inicio().isAfter(ahora)));
        if (!disponible) {
            throw noDisponible(cupo, estado);
        }
        if (canal == CanalReserva.EN_LINEA) {
            if (!cupo.isHabilitadoWeb()) {
                throw ErrorNegocio.conflicto("CUPO_NO_WEB", "Este horario no está disponible en la reserva en línea. Elige otro.");
            }
            ReglaReservaWeb regla = new ReglaReservaWeb(null, parametros.topeReservasWeb().orElse(null));
            long activas = citas.contarActivasEnLinea(idPaciente, ahora.toLocalDate());
            if (regla.superaTope(activas)) {
                throw new ErrorNegocio(ErrorNegocio.Tipo.REGLA_NEGOCIO, "TOPE_WEB", "Ya tienes " + activas
                        + (activas == 1 ? " hora reservada" : " horas reservadas")
                        + " en línea. Para pedir otra, acércate a tu policlínico o llama por teléfono.",
                        Map.of("tope", regla.getTope()));
            }
        }
        // 4. Crea la cita (TENS → RESERVADA; EN_LINEA sin verificación → PENDIENTE_VERIFICACION).
        Cita cita = Cita.nueva(paciente, cupo, canal, idUsuario, habilitado, serie, ahora);
        try {
            cita = citas.guardar(cita);
        } catch (DataIntegrityViolationException e) {
            // 8. Segunda barrera: uq_cita_cupo_activo.
            throw TraductorErroresBaseDatos.traducir(e)
                    .orElse(ErrorNegocio.conflicto("CUPO_TOMADO", TraductorErroresBaseDatos.MSJ_CUPO_TOMADO));
        } catch (RuntimeException e) {
            throw TraductorErroresBaseDatos.traducir(e).orElseThrow(() -> e);
        }
        // 5. Mensajes en la misma transacción, uno por destinatario (D-07).
        notificaciones.programarConfirmacion(cita);
        notificaciones.programarRecordatorios(cita);
        // 6. Auditoría.
        auditoria.registrarComo(idUsuario, AccionAuditoria.CREAR, "cita", cita.getId(),
                "Paciente " + idPaciente + ", cupo " + idCupo + ", canal " + canal
                        + (cita.getEstado() == EstadoCita.PENDIENTE_VERIFICACION ? ", pendiente de verificación" : ""));
        return cita;
    }

    // ---------------- Serie de sesiones (RF-16, D-06) ----------------

    @Transactional(readOnly = true)
    public PrevisualizacionSerie previsualizarSerie(Integer idProfesional, Long idCupoPrimero, int cantidad) {
        Cupo primero = cupos.buscar(idCupoPrimero).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese horario."));
        Integer profesional = idProfesional == null ? primero.getBloque().getProfesional().getId() : idProfesional;
        if (!primero.getBloque().getProfesional().getId().equals(profesional)) {
            throw ErrorNegocio.validacion("idCupoPrimero", "El primer horario debe ser del mismo profesional.");
        }
        LocalDateTime ahora = reloj.ahora();
        List<FechaSerie> con = new ArrayList<>();
        List<FechaSerie> sin = new ArrayList<>();
        for (LocalDate f : SerieSesiones.fechas(primero.getFecha(), cantidad)) {
            Optional<Cupo> c = cupos.buscarPorProfesionalFechaHora(profesional, f, primero.getHoraInicio());
            String motivo = null;
            if (c.isEmpty()) {
                motivo = "No hay horario ese día";
            } else {
                EstadoCupo e = c.get().estado(ahora, cupos.tieneCitaVigente(c.get().getId()));
                if (e == EstadoCupo.BLOQUEADO) motivo = "Horario bloqueado";
                else if (e == EstadoCupo.RESERVADO) motivo = "Horario ya reservado";
                else if (e == EstadoCupo.PASADO) motivo = "La hora ya pasó";
            }
            if (motivo == null) {
                con.add(new FechaSerie(f, primero.getHoraInicio(), c.get().getId(), null));
            } else {
                sin.add(new FechaSerie(f, primero.getHoraInicio(), null, motivo));
            }
        }
        return new PrevisualizacionSerie(con, sin);
    }

    /** D-06: la TENS/TONS decide; se reservan solo las fechas aceptadas que siguen con cupo. */
    public List<Cita> reservarSerie(Integer idPaciente, Integer idProfesional, Long idCupoPrimero, int cantidad,
                                    List<LocalDate> fechasAceptadas) {
        UsuarioSesion u = SesionActual.usuario();
        PrevisualizacionSerie vista = previsualizarSerie(idProfesional, idCupoPrimero, cantidad);
        Set<LocalDate> aceptadas = fechasAceptadas == null ? Set.of() : new HashSet<>(fechasAceptadas);
        List<FechaSerie> elegidas = vista.conCupo().stream()
                .filter(f -> aceptadas.isEmpty() || aceptadas.contains(f.fecha())).toList();
        if (elegidas.isEmpty()) {
            throw ErrorNegocio.regla("SERIE_SIN_CUPOS", "No hay horarios disponibles para reservar en la serie.");
        }
        Cupo primero = cupos.buscar(idCupoPrimero).orElseThrow();
        Paciente paciente = pacientes.obtener(idPaciente);
        if (!paciente.estaHabilitado()) {
            throw ErrorNegocio.regla("PACIENTE_NO_HABILITADO", TraductorErroresBaseDatos.MSJ_NO_HABILITADO);
        }
        SerieSesiones serie = citas.guardarSerie(new SerieSesiones(paciente, primero.getBloque().getProfesional(), cantidad,
                primero.getFecha(), primero.getHoraInicio(), u.idUsuario(), reloj.ahora()));
        List<Cita> creadas = new ArrayList<>();
        for (FechaSerie f : elegidas) {
            creadas.add(reservarInterno(idPaciente, f.idCupo(), CanalReserva.TENS, u.idUsuario(), serie));
        }
        auditoria.registrar(AccionAuditoria.CREAR, "serie_sesiones", serie.getId(),
                creadas.size() + " de " + cantidad + " sesiones reservadas");
        return creadas;
    }

    // ---------------- Anular (RF-17, RF-33, RN-07) ----------------

    public Cita anular(Long idCita, OrigenAnulacion origen, String motivo) {
        Integer idUsuario = SesionActual.opcional().map(UsuarioSesion::idUsuario).orElse(null);
        return anularComo(idCita, origen, motivo, idUsuario, true);
    }

    private Cita anularComo(Long idCita, OrigenAnulacion origen, String motivo, Integer idUsuario, boolean avisarPaciente) {
        Cita cita = paraModificar(idCita);
        exigirPoliclinicoPermitido(cita);
        cita.anular(origen, motivo, idUsuario, reloj.ahora());
        citas.guardar(cita);
        notificaciones.cancelarProgramados(cita);
        if (avisarPaciente) {
            if (origen == OrigenAnulacion.PACIENTE_MENSAJE) {
                notificaciones.enviarAnulacion(cita, false);
            } else if (origen == OrigenAnulacion.ADMINISTRACION) {
                notificaciones.enviarAnulacion(cita, true);
            }
        }
        listaEspera.avisarCupoLiberado(cita.getCupo(), cita.getId());
        auditoria.registrarComo(idUsuario, AccionAuditoria.ANULAR, "cita", cita.getId(),
                "Origen " + origen + (motivo == null || motivo.isBlank() ? "" : ": " + motivo));
        return cita;
    }

    // ---------------- Reagendar (D-11) ----------------

    public Cita reagendar(Long idCita, Long idCupoNuevo) {
        UsuarioSesion u = SesionActual.usuario();
        Cita original = paraModificar(idCita);
        if (!original.estaVigente()) {
            throw ErrorNegocio.regla("CITA_NO_VIGENTE", "Esta cita ya no está vigente.");
        }
        OrigenAnulacion origen = u.esAdministracion() ? OrigenAnulacion.ADMINISTRACION : OrigenAnulacion.TENS;
        CanalReserva canal = original.getCanalReserva();
        cupos.obtenerParaReserva(idCupoNuevo).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese horario."));
        if (cupos.tieneCitaVigente(idCupoNuevo)) {
            throw ErrorNegocio.conflicto("CUPO_TOMADO", TraductorErroresBaseDatos.MSJ_CUPO_TOMADO);
        }
        anularComo(idCita, origen, "Reagendada", u.idUsuario(), false);
        Integer idUsuarioReserva = canal == CanalReserva.TENS ? u.idUsuario() : null;
        return reservarSinValidarWeb(original.getPaciente().getId(), idCupoNuevo, canal, idUsuarioReserva);
    }

    /** En una reasignación se conserva el canal original; no se exige que el cupo nuevo sea web. */
    private Cita reservarSinValidarWeb(Integer idPaciente, Long idCupo, CanalReserva canal, Integer idUsuario) {
        if (canal == CanalReserva.EN_LINEA) {
            LocalDateTime ahora = reloj.ahora();
            Cupo cupo = cupos.obtenerParaReserva(idCupo).orElseThrow();
            boolean reservado = cupos.tieneCitaVigente(idCupo);
            if (!cupo.getBloque().isActivo() || !cupo.estaDisponible(ahora, reservado)) {
                throw noDisponible(cupo, cupo.estado(ahora, reservado));
            }
            Paciente p = pacientes.obtener(idPaciente);
            Cita cita = citas.guardar(Cita.nueva(p, cupo, canal, null, p.estaHabilitado(), null, ahora));
            notificaciones.programarConfirmacion(cita);
            notificaciones.programarRecordatorios(cita);
            auditoria.registrar(AccionAuditoria.CREAR, "cita", cita.getId(), "Reagendada (canal en línea)");
            return cita;
        }
        return reservarInterno(idPaciente, idCupo, canal, idUsuario, null);
    }

    /** D-10: resuelve las reservas afectadas por un cambio de bloque (Administración). */
    public List<Cita> resolverAfectadas(Integer idBloque, List<AccionAfectada> acciones) {
        List<Cita> resultado = new ArrayList<>();
        for (AccionAfectada a : acciones) {
            Cita afectada = paraModificar(a.idCita());
            if (!afectada.getCupo().getBloque().getId().equals(idBloque)) {
                throw ErrorNegocio.validacion("acciones", "Una de las citas no pertenece a este bloque horario.");
            }
            if (a.accion() == AccionAfectada.Tipo.REASIGNAR) {
                if (a.idCupoNuevo() == null) {
                    throw ErrorNegocio.validacion("idCupoNuevo", "Elige el nuevo horario para reasignar.");
                }
                resultado.add(reagendar(a.idCita(), a.idCupoNuevo()));
            } else {
                resultado.add(anular(a.idCita(), OrigenAnulacion.ADMINISTRACION, "Cambio en el horario del profesional"));
            }
        }
        return resultado;
    }

    public record AccionAfectada(Long idCita, Tipo accion, Long idCupoNuevo) {
        public enum Tipo { REASIGNAR, ANULAR }
    }

    // ---------------- Consultas ----------------

    /** 409 con la causa real: solo «otra persona lo tomó» cuando de verdad tiene una reserva. */
    private static ErrorNegocio noDisponible(Cupo cupo, EstadoCupo estado) {
        if (estado == EstadoCupo.RESERVADO) {
            return ErrorNegocio.conflicto("CUPO_TOMADO", TraductorErroresBaseDatos.MSJ_CUPO_TOMADO);
        }
        String mensaje = !cupo.getBloque().isActivo() || estado == EstadoCupo.BLOQUEADO
                ? "Este horario está bloqueado y no se puede reservar. Elige otro."
                : estado == EstadoCupo.PASADO ? "Este horario ya pasó. Elige otro."
                : "Este horario todavía no está publicado. Elige otro.";
        return ErrorNegocio.conflicto("CUPO_NO_DISPONIBLE", mensaje);
    }

    /** Con bloqueo de fila: dos operaciones simultáneas sobre la misma cita se atienden una después de la otra. */
    private Cita paraModificar(Long idCita) {
        return citas.buscarParaModificar(idCita).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos esa cita."));
    }

    @Transactional(readOnly = true)
    public Cita cita(Long idCita) {
        return citas.buscar(idCita).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos esa cita."));
    }

    @Transactional(readOnly = true)
    public List<Cita> historial(Integer idPaciente) {
        return citas.buscarPorPaciente(idPaciente);
    }

    // ---------------- Eventos de otros módulos ----------------

    /** Al registrar una verificación HABILITADO, las citas PENDIENTE_VERIFICACION pasan a RESERVADA. */
    @EventListener
    public void alVerificar(VerificacionRegistrada evento) {
        if (evento.resultado() != ResultadoVerificacion.HABILITADO) {
            return;
        }
        for (Cita c : citas.pendientesDeVerificacion(evento.idPaciente())) {
            if (c.marcarVerificada()) {
                citas.guardar(c);
                auditoria.registrar(AccionAuditoria.MODIFICAR, "cita", c.getId(), "Verificada: pasa a reservada");
            }
        }
    }

    /** «No podré asistir» por WhatsApp o aviso telefónico. */
    @EventListener
    public void alSolicitarAnulacion(SolicitudAnulacion s) {
        Cita c = cita(s.idCita());
        if (c.estaVigente()) {
            anularComo(s.idCita(), s.origen(), s.motivo(), s.idUsuario(), true);
        }
    }

    private void exigirPoliclinicoPermitido(Cita cita) {
        // La TENS/TONS puede anular citas de cualquier policlínico (puede reservar en otras sedes).
    }

    public static String describir(Cita c) {
        Cupo cupo = c.getCupo();
        return Textos.capitalizar(Textos.fechaLarga(cupo.getFecha())) + ", " + Textos.hora2(cupo.getHoraInicio());
    }
}
