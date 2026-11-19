package cl.udd.hermandad.agenda.pacientes.dominio;

import jakarta.persistence.*;

/** RF-12, RN-05. Una persona puede ser responsable de varios pacientes. */
@Entity
@Table(name = "adulto_responsable")
public class AdultoResponsable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_adulto")
    private Integer id;

    @Embedded
    private DocumentoIdentidad documento;

    @Column(name = "nombre_completo", nullable = false, length = 120)
    private String nombreCompleto;

    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

    protected AdultoResponsable() {
    }

    public AdultoResponsable(DocumentoIdentidad documento, String nombreCompleto, String telefono) {
        this.documento = documento;
        actualizar(nombreCompleto, telefono);
    }

    public void actualizar(String nombreCompleto, String telefono) {
        this.nombreCompleto = nombreCompleto.trim();
        this.telefono = Telefono.normalizar("adulto.telefono", telefono);
    }

    public Integer getId() { return id; }
    public DocumentoIdentidad getDocumento() { return documento; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getTelefono() { return telefono; }
}
