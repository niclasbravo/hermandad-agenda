package cl.udd.hermandad.agenda.seguridad.dominio;

import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import jakarta.persistence.*;

@Entity
@Table(name = "perfil")
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_perfil", columnDefinition = "tinyint unsigned")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 40)
    private String nombre;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    protected Perfil() {
    }

    public Perfil(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public PerfilUsuario tipo() {
        return PerfilUsuario.desdeNombre(nombre);
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
}
