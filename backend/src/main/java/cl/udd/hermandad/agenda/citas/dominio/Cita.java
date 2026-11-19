package cl.udd.hermandad.agenda.citas.dominio;

import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.TraductorErroresBaseDatos;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * RF-15, RF-17, RF-22, RF-23, RF-34. Las transiciones se aplican aquí (sección 4.1). Nunca se borra (D-17).
 * La columna generada id_cupo_activo no se mapea: la calcula MySQL.
 */
@Entity
@Table(name = "cita")
public class Cita {

    private static final EnumSet<EstadoCita> VIGENTES = EnumSet.of(EstadoCita.RESERVADA, EstadoCita.PENDIENTE_VERIFICACION);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cupo", nullable = false)
    private Cupo cupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_serie")
    private SerieSesiones serie;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_reserva", nullable = false)
    private CanalReserva canalReserva;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoCita estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_confirmacion", nullable = false)
    private EstadoConfirmacion estadoConfirmacion;

    @Column(name = "id_usuario_reserva")
    private Integer idUsuarioReserva;

    @Column(name = "fecha_reserva", nullable = false)
    private LocalDateTime fechaReserva;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_anulacion")
    private OrigenAnulacion origenAnulacion;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "id_usuario_anulacion")
    private Integer idUsuarioAnulacion;

    @Column(name = "fecha_asistencia")
    private LocalDateTime fechaAsistencia;

    @Column(name = "id_usuario_asistencia")
    private Integer idUsuarioAsistencia;

    protected Cita() {
    }

    /**
     * Nueva cita. TENS + paciente no habilitado → 422 (RN-01). EN_LINEA + no habilitado → PENDIENTE_VERIFICACION (RN-13).
     */
    public static Cita nueva(Paciente paciente, Cupo cupo, CanalReserva canal, Integer idUsuario, boolean habilitado,
                             SerieSesiones serie, LocalDateTime ahora) {
        if (canal == CanalReserva.TENS && idUsuario == null) {
            throw new IllegalArgumentException("La reserva de la TENS/TONS requiere el usuario");
        }
        if (canal == CanalReserva.TENS && !habilitado) {
            throw ErrorNegocio.regla("PACIENTE_NO_HABILITADO", TraductorErroresBaseDatos.MSJ_NO_HABILITADO);
        }
        Cita c = new Cita();
        c.paciente = paciente;
        c.cupo = cupo;
        c.serie = serie;
        c.canalReserva = canal;
        c.idUsuarioReserva = canal == CanalReserva.EN_LINEA ? null : idUsuario;
        c.estado = habilitado ? EstadoCita.RESERVADA : EstadoCita.PENDIENTE_VERIFICACION;
        c.estadoConfirmacion = EstadoConfirmacion.SIN_ENVIAR;
        c.fechaReserva = ahora;
        return c;
    }

    /** RESERVADA | PENDIENTE_VERIFICACION → ANULADA. Exige fecha y origen. */
    public void anular(OrigenAnulacion origen, String motivo, Integer idUsuario, LocalDateTime ahora) {
        if (origen == null) {
            throw new IllegalArgumentException("La anulación requiere el origen");
        }
        if (!estaVigente()) {
            throw ErrorNegocio.regla("CITA_NO_ANULABLE", "Esta cita ya está " + nombreEstado() + " y no se puede anular.");
        }
        this.estado = EstadoCita.ANULADA;
        this.origenAnulacion = origen;
        this.motivoAnulacion = motivo == null || motivo.isBlank() ? null
                : (motivo.trim().length() > 200 ? motivo.trim().substring(0, 200) : motivo.trim());
        this.fechaAnulacion = ahora;
        this.idUsuarioAnulacion = idUsuario;
    }

    /** RESERVADA → ASISTIDA | INASISTIDA, una sola vez y desde el día de la cita. */
    public void registrarAsistencia(boolean asistio, Integer idUsuario, LocalDateTime ahora) {
        switch (estado) {
            case ANULADA -> throw ErrorNegocio.regla("CITA_ANULADA", "Esta cita está anulada y no admite registro de asistencia.");
            case PENDIENTE_VERIFICACION -> throw ErrorNegocio.regla("CITA_PENDIENTE",
                    "Primero verifica los certificados de FONASA y CESFAM del paciente.");
            case ASISTIDA, INASISTIDA -> throw ErrorNegocio.regla("ASISTENCIA_REGISTRADA", "La asistencia de esta cita ya fue registrada.");
            case RESERVADA -> {
            }
        }
        if (ahora.toLocalDate().isBefore(cupo.getFecha())) {
            throw ErrorNegocio.regla("ASISTENCIA_ANTICIPADA", "La asistencia se registra el día de la cita o después.");
        }
        this.estado = asistio ? EstadoCita.ASISTIDA : EstadoCita.INASISTIDA;
        this.fechaAsistencia = ahora;
        this.idUsuarioAsistencia = idUsuario;
    }

    /** PENDIENTE_VERIFICACION → RESERVADA al registrar una verificación HABILITADO. */
    public boolean marcarVerificada() {
        if (estado == EstadoCita.PENDIENTE_VERIFICACION) {
            estado = EstadoCita.RESERVADA;
            return true;
        }
        return false;
    }

    /** Por llamar: vigente para hoy o mañana, con confirmación no enviada o sin respuesta. */
    public boolean requiereLlamada(LocalDate hoy) {
        LocalDate fecha = cupo.getFecha();
        boolean hoyOMañana = fecha.equals(hoy) || fecha.equals(hoy.plusDays(1));
        return estaVigente() && hoyOMañana
                && (estadoConfirmacion == EstadoConfirmacion.NO_ENVIADA || estadoConfirmacion == EstadoConfirmacion.SIN_RESPUESTA);
    }

    public boolean estaVigente() {
        return VIGENTES.contains(estado);
    }

    // ---- Transiciones de la confirmación (sección 4.2) ----

    public void marcarConfirmacionEnviada() {
        if (estadoConfirmacion == EstadoConfirmacion.SIN_ENVIAR || estadoConfirmacion == EstadoConfirmacion.NO_ENVIADA) {
            estadoConfirmacion = EstadoConfirmacion.ENVIADA;
        }
    }

    public void marcarConfirmacionNoEnviada() {
        if (estadoConfirmacion == EstadoConfirmacion.SIN_ENVIAR || estadoConfirmacion == EstadoConfirmacion.ENVIADA) {
            estadoConfirmacion = EstadoConfirmacion.NO_ENVIADA;
        }
    }

    public void marcarSinRespuesta() {
        if (estadoConfirmacion != EstadoConfirmacion.CONFIRMADA && estadoConfirmacion != EstadoConfirmacion.CONFIRMADA_TELEFONO) {
            estadoConfirmacion = EstadoConfirmacion.SIN_RESPUESTA;
        }
    }

    public void confirmar() {
        if (estaVigente()) {
            estadoConfirmacion = EstadoConfirmacion.CONFIRMADA;
        }
    }

    public void confirmarPorTelefono() {
        if (!estaVigente()) {
            throw ErrorNegocio.regla("CITA_NO_VIGENTE", "Esta cita ya no está vigente.");
        }
        estadoConfirmacion = EstadoConfirmacion.CONFIRMADA_TELEFONO;
    }

    public String nombreEstado() {
        return switch (estado) {
            case PENDIENTE_VERIFICACION -> "pendiente de verificación";
            case RESERVADA -> "reservada";
            case ANULADA -> "anulada";
            case ASISTIDA -> "registrada como asistida";
            case INASISTIDA -> "registrada como no asistida";
        };
    }

    public Long getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public Cupo getCupo() { return cupo; }
    public SerieSesiones getSerie() { return serie; }
    public CanalReserva getCanalReserva() { return canalReserva; }
    public EstadoCita getEstado() { return estado; }
    public EstadoConfirmacion getEstadoConfirmacion() { return estadoConfirmacion; }
    public Integer getIdUsuarioReserva() { return idUsuarioReserva; }
    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public OrigenAnulacion getOrigenAnulacion() { return origenAnulacion; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public LocalDateTime getFechaAnulacion() { return fechaAnulacion; }
    public Integer getIdUsuarioAnulacion() { return idUsuarioAnulacion; }
    public LocalDateTime getFechaAsistencia() { return fechaAsistencia; }
    public Integer getIdUsuarioAsistencia() { return idUsuarioAsistencia; }
}
