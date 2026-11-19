package cl.udd.hermandad.agenda.listaespera.dominio;

import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** RF-25. Orden de llegada por fecha_orden (RN-06); cambia solo si la TENS/TONS adelanta (RF-42). */
@Entity
@Table(name = "inscripcion_espera")
public class InscripcionEspera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inscripcion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_policlinico", nullable = false, columnDefinition = "smallint unsigned")
    private Policlinico policlinico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profesion", nullable = false, columnDefinition = "smallint unsigned")
    private Profesion profesion;

    @Column(name = "fecha_inscripcion", nullable = false)
    private LocalDateTime fechaInscripcion;

    @Column(name = "fecha_orden", nullable = false)
    private LocalDateTime fechaOrden;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoInscripcion estado = EstadoInscripcion.EN_ESPERA;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_retiro")
    private MotivoRetiro motivoRetiro;

    @Column(name = "id_cita_asignada")
    private Long idCitaAsignada;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @OneToMany(mappedBy = "inscripcion", cascade = CascadeType.PERSIST)
    @OrderBy("fechaHora ASC, id ASC")
    private List<MovimientoEspera> movimientos = new ArrayList<>();

    protected InscripcionEspera() {
    }

    public InscripcionEspera(Paciente paciente, Policlinico policlinico, Profesion profesion, Integer idUsuario,
                             LocalDateTime ahora) {
        this.paciente = paciente;
        this.policlinico = policlinico;
        this.profesion = profesion;
        this.idUsuario = idUsuario;
        this.fechaInscripcion = ahora;
        this.fechaOrden = ahora;
        movimientos.add(new MovimientoEspera(this, TipoMovimiento.INSCRIPCION, null, idUsuario, ahora));
    }

    /** RF-42: motivo obligatorio. La nueva fecha de orden la calcula el servicio según la posición pedida. */
    public void adelantar(String motivo, LocalDateTime nuevaFechaOrden, Integer idUsuario, LocalDateTime ahora) {
        exigirEnEspera();
        if (motivo == null || motivo.isBlank()) {
            throw ErrorNegocio.validacion("motivo", "Escribe el motivo para adelantar a esta persona.");
        }
        this.fechaOrden = nuevaFechaOrden;
        movimientos.add(new MovimientoEspera(this, TipoMovimiento.ADELANTO, motivo.trim(), idUsuario, ahora));
    }

    /** RF-43, D-05. Si se le asignó una hora, la inscripción queda ligada a esa cita. */
    public void retirar(MotivoRetiro motivo, String detalle, Long idCita, Integer idUsuario, LocalDateTime ahora) {
        exigirEnEspera();
        if (motivo == null) {
            throw ErrorNegocio.validacion("motivo", "Elige el motivo para quitar a esta persona de la lista.");
        }
        if (motivo == MotivoRetiro.OTRO && (detalle == null || detalle.isBlank())) {
            throw ErrorNegocio.validacion("detalle", "Escribe el motivo.");
        }
        if (idCita != null && motivo != MotivoRetiro.HORA_ASIGNADA) {
            throw ErrorNegocio.validacion("idCita", "Solo se liga una cita cuando se le asignó una hora.");
        }
        this.estado = EstadoInscripcion.RETIRADA;
        this.motivoRetiro = motivo;
        this.idCitaAsignada = idCita;
        String texto = motivo.texto() + (detalle == null || detalle.isBlank() ? "" : ": " + detalle.trim());
        movimientos.add(new MovimientoEspera(this, TipoMovimiento.RETIRO, texto, idUsuario, ahora));
    }

    private void exigirEnEspera() {
        if (estado != EstadoInscripcion.EN_ESPERA) {
            throw ErrorNegocio.regla("INSCRIPCION_RETIRADA", "Esta persona ya no está en la lista de espera.");
        }
    }

    public Integer getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public Policlinico getPoliclinico() { return policlinico; }
    public Profesion getProfesion() { return profesion; }
    public LocalDateTime getFechaInscripcion() { return fechaInscripcion; }
    public LocalDateTime getFechaOrden() { return fechaOrden; }
    public EstadoInscripcion getEstado() { return estado; }
    public MotivoRetiro getMotivoRetiro() { return motivoRetiro; }
    public Long getIdCitaAsignada() { return idCitaAsignada; }
    public Integer getIdUsuario() { return idUsuario; }
    public List<MovimientoEspera> getMovimientos() { return movimientos; }
}
