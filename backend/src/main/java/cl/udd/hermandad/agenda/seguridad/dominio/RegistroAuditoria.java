package cl.udd.hermandad.agenda.seguridad.dominio;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RNF-05. id_usuario NULL = acción del sistema o del paciente en línea. */
@Entity
@Table(name = "auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long id;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false)
    private AccionAuditoria accion;

    @Column(name = "entidad", nullable = false, length = 40)
    private String entidad;

    @Column(name = "id_registro", nullable = false)
    private Long idRegistro;

    @Column(name = "detalle", length = 500)
    private String detalle;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Integer idUsuario, LocalDateTime fechaHora, AccionAuditoria accion, String entidad,
                             Long idRegistro, String detalle) {
        this.idUsuario = idUsuario;
        this.fechaHora = fechaHora;
        this.accion = accion;
        this.entidad = entidad;
        this.idRegistro = idRegistro;
        this.detalle = detalle == null ? null : (detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
    }

    public Long getId() { return id; }
    public Integer getIdUsuario() { return idUsuario; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public AccionAuditoria getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public Long getIdRegistro() { return idRegistro; }
    public String getDetalle() { return detalle; }
}
