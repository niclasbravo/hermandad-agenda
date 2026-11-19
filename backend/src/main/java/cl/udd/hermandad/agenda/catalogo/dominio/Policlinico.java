package cl.udd.hermandad.agenda.catalogo.dominio;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "policlinico")
public class Policlinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_policlinico", columnDefinition = "smallint unsigned")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "direccion", nullable = false, length = 150)
    private String direccion;

    @Column(name = "comuna", nullable = false, length = 60)
    private String comuna;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "policlinico", cascade = CascadeType.ALL)
    private List<Box> boxes = new ArrayList<>();

    protected Policlinico() {
    }

    public Policlinico(String nombre, String direccion, String comuna) {
        actualizar(nombre, direccion, comuna);
    }

    public void actualizar(String nombre, String direccion, String comuna) {
        this.nombre = nombre.trim();
        this.direccion = direccion.trim();
        this.comuna = comuna.trim();
    }

    public Box agregarBox(String nombreBox) {
        Box box = new Box(this, nombreBox.trim());
        boxes.add(box);
        return box;
    }

    public List<Box> boxesOrdenados() {
        return boxes.stream().sorted(Comparator.comparing(Box::getNombre, Policlinico::compararNatural)).toList();
    }

    static int compararNatural(String a, String b) {
        String na = a.replaceAll("\\D", "");
        String nb = b.replaceAll("\\D", "");
        if (!na.isEmpty() && !nb.isEmpty() && a.replaceAll("\\d", "").equals(b.replaceAll("\\d", ""))) {
            return Integer.compare(Integer.parseInt(na), Integer.parseInt(nb));
        }
        return a.compareTo(b);
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getComuna() { return comuna; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<Box> getBoxes() { return boxes; }
}
