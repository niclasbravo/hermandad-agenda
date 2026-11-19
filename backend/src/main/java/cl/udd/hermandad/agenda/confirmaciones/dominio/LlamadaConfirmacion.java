package cl.udd.hermandad.agenda.confirmaciones.dominio;

import cl.udd.hermandad.agenda.citas.dominio.Cita;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RF-39. Confirmación telefónica con comentario (D-15). */
@Entity
@Table(name = "llamada_confirmacion")
public class LlamadaConfirmacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_llamada")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cita", nullable = false)
    private Cita cita;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false)
    private ResultadoLlamada resultado;

    @Column(name = "comentario", length = 500)
    private String comentario;

    protected LlamadaConfirmacion() {
    }

    public LlamadaConfirmacion(Cita cita, Integer idUsuario, ResultadoLlamada resultado, String comentario, LocalDateTime ahora) {
        this.cita = cita;
        this.idUsuario = idUsuario;
        this.resultado = resultado;
        this.comentario = comentario == null || comentario.isBlank() ? null
                : (comentario.trim().length() > 500 ? comentario.trim().substring(0, 500) : comentario.trim());
        this.fechaHora = ahora;
    }

    public Long getId() { return id; }
    public Cita getCita() { return cita; }
    public Integer getIdUsuario() { return idUsuario; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public ResultadoLlamada getResultado() { return resultado; }
    public String getComentario() { return comentario; }
}
