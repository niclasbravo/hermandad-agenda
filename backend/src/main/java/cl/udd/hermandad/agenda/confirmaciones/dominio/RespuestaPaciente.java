package cl.udd.hermandad.agenda.confirmaciones.dominio;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RF-21, RF-33. Vale la última respuesta. */
@Entity
@Table(name = "respuesta_mensaje")
public class RespuestaPaciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_respuesta")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_mensaje", nullable = false)
    private Mensaje mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "respuesta", nullable = false)
    private Respuesta respuesta;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    protected RespuestaPaciente() {
    }

    public RespuestaPaciente(Mensaje mensaje, Respuesta respuesta, LocalDateTime fechaHora) {
        this.mensaje = mensaje;
        this.respuesta = respuesta;
        this.fechaHora = fechaHora;
    }

    public Long getId() { return id; }
    public Mensaje getMensaje() { return mensaje; }
    public Respuesta getRespuesta() { return respuesta; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
