package cl.udd.hermandad.agenda.catalogo.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "box")
public class Box {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_box", columnDefinition = "smallint unsigned")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_policlinico", nullable = false, columnDefinition = "smallint unsigned")
    private Policlinico policlinico;

    @Column(name = "nombre", nullable = false, length = 30)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Box() {
    }

    Box(Policlinico policlinico, String nombre) {
        this.policlinico = policlinico;
        this.nombre = nombre;
    }

    public void actualizar(String nombre, boolean activo) {
        this.nombre = nombre.trim();
        this.activo = activo;
    }

    public Integer getId() { return id; }
    public Policlinico getPoliclinico() { return policlinico; }
    public String getNombre() { return nombre; }
    public boolean isActivo() { return activo; }
}
