package cl.udd.hermandad.agenda.agenda.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RF-07, D-12. Solo la Administración bloquea y siempre con motivo. */
@Entity
@Table(name = "bloqueo")
public class Bloqueo {

    public static final String MOTIVO_BLOQUE_MODIFICADO = "Bloque modificado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bloqueo")
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "motivo", nullable = false, length = 150)
    private String motivo;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected Bloqueo() {
    }

    public Bloqueo(Integer idUsuario, String motivo, LocalDateTime ahora) {
        if (motivo == null || motivo.isBlank()) {
            throw ErrorNegocio.validacion("motivo", "Escribe el motivo del bloqueo.");
        }
        this.idUsuario = idUsuario;
        this.motivo = motivo.trim().length() > 150 ? motivo.trim().substring(0, 150) : motivo.trim();
        this.fechaRegistro = ahora;
    }

    public Integer getId() { return id; }
    public Integer getIdUsuario() { return idUsuario; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
