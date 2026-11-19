package cl.udd.hermandad.agenda.reportes.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import jakarta.persistence.*;

/** RF-30, D-13. Tramos de edad que define la Administración. */
@Entity
@Table(name = "categoria_etaria")
public class CategoriaEtaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria", columnDefinition = "tinyint unsigned")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 40)
    private String nombre;

    @Column(name = "edad_desde", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer edadDesde;

    @Column(name = "edad_hasta", columnDefinition = "tinyint unsigned")
    private Integer edadHasta;

    protected CategoriaEtaria() {
    }

    public CategoriaEtaria(String nombre, Integer edadDesde, Integer edadHasta) {
        actualizar(nombre, edadDesde, edadHasta);
    }

    public void actualizar(String nombre, Integer edadDesde, Integer edadHasta) {
        if (nombre == null || nombre.isBlank()) {
            throw ErrorNegocio.validacion("nombre", "Escribe el nombre del tramo.");
        }
        if (edadDesde == null || edadDesde < 0 || edadDesde > 120 || (edadHasta != null && edadHasta < edadDesde)) {
            throw ErrorNegocio.validacion("edadHasta", "Revisa las edades del tramo.");
        }
        this.nombre = nombre.trim();
        this.edadDesde = edadDesde;
        this.edadHasta = edadHasta;
    }

    public boolean incluye(int edad) {
        return edad >= edadDesde && (edadHasta == null || edad <= edadHasta);
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public Integer getEdadDesde() { return edadDesde; }
    public Integer getEdadHasta() { return edadHasta; }
}
