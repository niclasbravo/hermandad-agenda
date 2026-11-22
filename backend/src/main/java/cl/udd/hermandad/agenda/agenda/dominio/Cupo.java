package cl.udd.hermandad.agenda.agenda.dominio;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** RF-06. El estado se deriva; no se guarda dos veces. */
@Entity
@Table(name = "cupo")
public class Cupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cupo")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bloque", nullable = false)
    private BloqueHorario bloque;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_termino", nullable = false)
    private LocalTime horaTermino;

    @Column(name = "habilitado_web", nullable = false)
    private boolean habilitadoWeb;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_bloqueo")
    private Bloqueo bloqueo;

    protected Cupo() {
    }

    public Cupo(BloqueHorario bloque, LocalDate fecha, LocalTime horaInicio, LocalTime horaTermino) {
        this.bloque = bloque;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaTermino = horaTermino;
    }

    /**
     * Estado derivado. {@code reservado} indica si tiene una cita vigente (cita.id_cupo_activo = id_cupo).
     */
    public EstadoCupo estado(LocalDateTime ahora, boolean reservado) {
        if (bloqueo != null) {
            return EstadoCupo.BLOQUEADO;
        }
        if (reservado) {
            return EstadoCupo.RESERVADO;
        }
        if (!estaPublicado(ahora.toLocalDate())) {
            return EstadoCupo.NO_PUBLICADO;
        }
        if (inicio().isAfter(ahora)) {
            return EstadoCupo.DISPONIBLE;
        }
        return EstadoCupo.PASADO;
    }

    public boolean estaDisponible(LocalDateTime ahora, boolean reservado) {
        return bloque.isActivo() && estado(ahora, reservado) == EstadoCupo.DISPONIBLE;
    }

    /** RN-08: se publica hasta hoy + plazo_publicacion_dias del profesional. */
    public boolean estaPublicado(LocalDate hoy) {
        int plazo = bloque.getProfesional().getPlazoPublicacionDias();
        return !fecha.isAfter(hoy.plusDays(plazo));
    }

    public LocalDateTime inicio() {
        return LocalDateTime.of(fecha, horaInicio);
    }

    public int duracionMinutos() {
        return (int) java.time.Duration.between(horaInicio, horaTermino).toMinutes();
    }

    public void bloquear(Bloqueo bloqueo) {
        this.bloqueo = bloqueo;
    }

    public void desbloquear() {
        this.bloqueo = null;
    }

    public void habilitarWeb(boolean habilitado) {
        this.habilitadoWeb = habilitado;
    }

    public Long getId() { return id; }
    public BloqueHorario getBloque() { return bloque; }
    public LocalDate getFecha() { return fecha; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraTermino() { return horaTermino; }
    public boolean isHabilitadoWeb() { return habilitadoWeb; }
    public Bloqueo getBloqueo() { return bloqueo; }
}
