package cl.udd.hermandad.agenda.listaespera.dominio;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RF-42, RF-43: quién adelantó o retiró, cuándo y por qué (CA-RF42-1). */
@Entity
@Table(name = "movimiento_espera")
public class MovimientoEspera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_inscripcion", nullable = false)
    private InscripcionEspera inscripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoMovimiento tipo;

    @Column(name = "motivo", length = 200)
    private String motivo;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    protected MovimientoEspera() {
    }

    MovimientoEspera(InscripcionEspera inscripcion, TipoMovimiento tipo, String motivo, Integer idUsuario, LocalDateTime ahora) {
        this.inscripcion = inscripcion;
        this.tipo = tipo;
        this.motivo = motivo == null ? null : (motivo.length() > 200 ? motivo.substring(0, 200) : motivo);
        this.idUsuario = idUsuario;
        this.fechaHora = ahora;
    }

    public Integer getId() { return id; }
    public InscripcionEspera getInscripcion() { return inscripcion; }
    public TipoMovimiento getTipo() { return tipo; }
    public String getMotivo() { return motivo; }
    public Integer getIdUsuario() { return idUsuario; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
