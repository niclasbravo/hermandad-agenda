package cl.udd.hermandad.agenda.asistencia.api;

import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.agenda.dominio.EstadoCupo;
import cl.udd.hermandad.agenda.asistencia.aplicacion.AsistenciaService;
import cl.udd.hermandad.agenda.asistencia.dominio.AgendaDelDia;
import cl.udd.hermandad.agenda.asistencia.dominio.ResumenDia;
import cl.udd.hermandad.agenda.asistencia.dominio.VistaAgenda;
import cl.udd.hermandad.agenda.citas.api.CitaDtos.CitaDto;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.citas.dominio.EstadoConfirmacion;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.RedactorMensajes;
import cl.udd.hermandad.agenda.confirmaciones.dominio.EstadoEntrega;
import cl.udd.hermandad.agenda.confirmaciones.dominio.Mensaje;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.dominio.IUsuarioRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.RegistroAuditoria;
import cl.udd.hermandad.agenda.seguridad.dominio.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/** M7 · CU-06. */
@RestController
@RequestMapping("/api")
public class AsistenciaController {

    public record FilaCita(CitaDto cita, boolean requiereLlamada, String avisadoA, boolean tieneDocumentos) {
    }

    public record CeldaCupo(Long idCupo, LocalTime horaInicio, LocalTime horaTermino, EstadoCupo estado, String motivoBloqueo,
                            FilaCita cita) {
    }

    public record Columna(Integer id, String titulo, String subtitulo, List<CeldaCupo> cupos) {
    }

    public record AgendaDiaDto(LocalDate fecha, Integer idPoliclinico, VistaAgenda vista, ResumenDia resumen,
                               List<FilaCita> citas, List<Columna> columnas) {
    }

    public record AsistenciaRequest(@NotNull(message = "Indica si asistió.") Boolean asistio) {
    }

    public record EventoDto(LocalDateTime fechaHora, String texto) {
    }

    public record MensajeDto(Long id, String tipo, String plantilla, String destinatario, String telefono, String estado,
                             String enviadoPor, LocalDateTime fechaProgramada, LocalDateTime fechaEnvio, String texto) {
        static MensajeDto de(Mensaje m) {
            return new MensajeDto(m.getId(), m.getPlantilla().getTipo().name(), m.getPlantilla().getNombreProveedor(),
                    m.getDestinatario().name(), cl.udd.hermandad.agenda.pacientes.dominio.Telefono.formatear(m.getTelefonoDestino()),
                    m.estadoVisible(), m.getEnviadoPor().name(), m.getFechaProgramada(), m.getFechaEnvio(),
                    RedactorMensajes.texto(m.getPlantilla(), m.getCita(), m.getDestinatario()));
        }
    }

    public record DetalleCitaDto(FilaCita fila, List<MensajeDto> mensajes, List<EventoDto> historial) {
    }

    private final AsistenciaService servicio;
    private final AgendamientoService agendamiento;
    private final NotificacionService notificaciones;
    private final ListaEsperaService listaEspera;
    private final PacienteService pacientes;
    private final AuditoriaService auditoria;
    private final IUsuarioRepository usuarios;
    private final Reloj reloj;

    public AsistenciaController(AsistenciaService servicio, AgendamientoService agendamiento,
                                NotificacionService notificaciones, ListaEsperaService listaEspera,
                                PacienteService pacientes, AuditoriaService auditoria, IUsuarioRepository usuarios,
                                Reloj reloj) {
        this.servicio = servicio;
        this.agendamiento = agendamiento;
        this.notificaciones = notificaciones;
        this.listaEspera = listaEspera;
        this.pacientes = pacientes;
        this.auditoria = auditoria;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @GetMapping("/agenda-dia")
    @Transactional(readOnly = true)
    public AgendaDiaDto agendaDia(@RequestParam(required = false) Integer policlinico,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  @RequestParam(required = false) VistaAgenda vista) {
        Integer pol = SesionActual.policlinicoFiltrado(policlinico);
        LocalDate f = fecha == null ? reloj.hoy() : fecha;
        VistaAgenda v = vista == null ? VistaAgenda.LISTA : vista;
        AgendaDelDia dia = servicio.agendaDelDia(pol, f);
        ResumenDia resumen = dia.resumen(listaEspera.contarEnEspera(pol));
        Map<Long, FilaCita> filas = new LinkedHashMap<>();
        for (Cita c : dia.vigentesOAtendidas()) {
            filas.put(c.getCupo().getId(), fila(c));
        }
        List<Columna> columnas = List.of();
        if (v != VistaAgenda.LISTA) {
            columnas = columnas(servicio.cuposDelDia(pol, f), filas, v);
        }
        return new AgendaDiaDto(f, pol, v, resumen, new ArrayList<>(filas.values()), columnas);
    }

    @PostMapping("/citas/{id}/asistencia")
    @PreAuthorize("hasRole('TENS')")
    public FilaCita asistencia(@PathVariable Long id, @Valid @RequestBody AsistenciaRequest r) {
        return fila(servicio.registrarAsistencia(id, r.asistio()));
    }

    @GetMapping("/citas/{id}")
    @Transactional(readOnly = true)
    public DetalleCitaDto detalle(@PathVariable Long id) {
        Cita c = agendamiento.cita(id);
        NotificacionService.Detalle d = notificaciones.detalle(id);
        Map<Integer, String> nombres = new HashMap<>();
        List<EventoDto> eventos = new ArrayList<>();
        for (RegistroAuditoria r : auditoria.historial("cita", id)) {
            eventos.add(new EventoDto(r.getFechaHora(), textoAuditoria(r) + quien(r.getIdUsuario(), nombres)));
        }
        for (Mensaje m : d.mensajes()) {
            if (m.getFechaEnvio() != null) {
                String que = switch (m.getPlantilla().getTipo()) {
                    case CONFIRMACION -> "Confirmación";
                    case RECORDATORIO -> "Recordatorio";
                    case REFUERZO -> "Refuerzo";
                    case ANULACION -> "Aviso de anulación";
                };
                String a = m.getDestinatario() == Paciente.Destinatario.PACIENTE ? "al paciente" : "al adulto responsable";
                // Los cancelados antes de la marca CANCELADO (datos antiguos) se reconocen por la fecha de anulación.
                boolean anteriorALaMarca = m.getEstadoEntrega() == EstadoEntrega.FALLIDO && c.getFechaAnulacion() != null
                        && m.getIdExterno() == null && !m.getFechaEnvio().isBefore(c.getFechaAnulacion());
                String estado = m.isCancelado() || anteriorALaMarca
                        ? (c.getFechaAnulacion() != null ? "cancelado (cita anulada)" : "cancelado (se reemplazó por uno nuevo)")
                        : estadoEntrega(m.getEstadoEntrega());
                eventos.add(new EventoDto(m.getFechaEnvio(), que + " por WhatsApp " + a + ": " + estado));
            }
        }
        d.respuestas().forEach(r -> eventos.add(new EventoDto(r.getFechaHora(), "Respuesta del paciente: "
                + (r.getRespuesta().name().equals("ASISTIRA") ? "«Asistiré»" : "«No podré asistir»"))));
        d.llamadas().forEach(l -> eventos.add(new EventoDto(l.getFechaHora(), "Llamada: " + switch (l.getResultado()) {
            case CONFIRMO -> "confirmó que asistirá";
            case NO_ASISTIRA -> "avisó que no asistirá";
            case NO_CONTESTO -> "no contestó";
        } + (l.getComentario() == null ? "" : " (" + l.getComentario() + ")") + quien(l.getIdUsuario(), nombres))));
        eventos.sort(Comparator.comparing(EventoDto::fechaHora));
        return new DetalleCitaDto(fila(c), d.mensajes().stream().map(MensajeDto::de).toList(), eventos);
    }

    private FilaCita fila(Cita c) {
        String avisadoA = null;
        Paciente p = c.getPaciente();
        if (p.getAdultoResponsable() != null && c.getEstadoConfirmacion() != EstadoConfirmacion.SIN_ENVIAR
                && c.getEstadoConfirmacion() != EstadoConfirmacion.NO_ENVIADA) {
            avisadoA = p.getAdultoResponsable().getNombreCompleto() + " (" + p.getRelacionAdulto() + ")";
        }
        boolean docs = c.getEstado().name().equals("PENDIENTE_VERIFICACION") && !pacientes.documentos(p.getId()).isEmpty();
        return new FilaCita(CitaDto.de(c, reloj.hoy()), c.requiereLlamada(reloj.hoy()), avisadoA, docs);
    }

    private List<Columna> columnas(List<CupoVista> cupos, Map<Long, FilaCita> filas, VistaAgenda vista) {
        Map<Integer, Columna> mapa = new LinkedHashMap<>();
        List<CupoVista> ordenados = new ArrayList<>(cupos);
        ordenados.sort(Comparator.comparing((CupoVista c) -> vista == VistaAgenda.POR_BOX ? c.box() : c.profesional())
                .thenComparing(CupoVista::horaInicio));
        if (vista == VistaAgenda.POR_BOX) {
            ordenados.sort(Comparator.comparing(CupoVista::idBox).thenComparing(CupoVista::horaInicio));
        }
        for (CupoVista c : ordenados) {
            boolean porBox = vista == VistaAgenda.POR_BOX;
            Integer clave = porBox ? c.idBox() : c.idProfesional();
            Columna col = mapa.computeIfAbsent(clave, k -> new Columna(k, porBox ? c.box() : c.profesional(),
                    porBox ? c.profesional() + ", " + c.profesion()
                           : c.profesion() + ", " + c.box() + ", " + c.duracion() + " min", new ArrayList<>()));
            col.cupos().add(new CeldaCupo(c.id(), c.horaInicio(), c.horaTermino(), c.estado(), c.motivoBloqueo(), filas.get(c.id())));
        }
        return new ArrayList<>(mapa.values());
    }

    private static String estadoEntrega(EstadoEntrega e) {
        return switch (e) {
            case PROGRAMADO -> "programado";
            case ENVIADO -> "enviado";
            case ENTREGADO -> "entregado";
            case LEIDO -> "leído";
            case FALLIDO -> "no enviado";
        };
    }

    private static String textoAuditoria(RegistroAuditoria r) {
        String d = r.getDetalle() == null ? "" : r.getDetalle();
        return switch (r.getAccion()) {
            case CREAR -> "Reserva registrada" + (d.contains("EN_LINEA") ? " en línea" : "")
                    + (d.contains("pendiente") ? " (pendiente de verificación)" : "");
            case ANULAR -> "Cita anulada" + (d.contains(":") ? " —" + d.substring(d.indexOf(':') + 1) : "");
            case MODIFICAR -> d.isBlank() ? "Cita modificada" : d;
            default -> r.getAccion().name().toLowerCase();
        };
    }

    private String quien(Integer idUsuario, Map<Integer, String> cache) {
        if (idUsuario == null) {
            return "";
        }
        String n = cache.computeIfAbsent(idUsuario, id -> usuarios.findById(id).map(Usuario::getNombreCompleto).orElse(""));
        return n.isBlank() ? "" : " · " + n;
    }
}
