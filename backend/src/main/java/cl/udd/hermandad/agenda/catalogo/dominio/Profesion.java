package cl.udd.hermandad.agenda.catalogo.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import jakarta.persistence.*;

/** RN-04, RF-05. La duración NULL significa «por definir»: no se generan cupos. */
@Entity
@Table(name = "profesion")
public class Profesion {

    public static final int DURACION_MINIMA = 5;
    public static final int DURACION_MAXIMA = 240;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_profesion", columnDefinition = "smallint unsigned")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "duracion_min", columnDefinition = "smallint unsigned")
    private Integer duracionMin;

    @Column(name = "duracion_alumno_min", columnDefinition = "smallint unsigned")
    private Integer duracionAlumnoMin;

    protected Profesion() {
    }

    public Profesion(String nombre, Integer duracionMin) {
        this.nombre = nombre;
        this.duracionMin = duracionMin;
    }

    public boolean tieneDuracion() {
        return duracionMin != null;
    }

    /** D-08: si atiende un alumno en práctica y hay duración de alumnos, se usa esa. */
    public Integer duracionPara(boolean esAlumno) {
        if (esAlumno && duracionAlumnoMin != null) {
            return duracionAlumnoMin;
        }
        return duracionMin;
    }

    /** CU-01 E2: rechaza 0, vacío o fuera de 5 a 240 minutos. La de alumnos es opcional. */
    public void definirDuracion(Integer minutos, Integer minutosAlumno) {
        if (minutos == null || minutos < DURACION_MINIMA || minutos > DURACION_MAXIMA) {
            throw ErrorNegocio.validacion("duracionMin", "Escribe una duración entre 5 y 240 minutos.");
        }
        if (minutosAlumno != null && (minutosAlumno < DURACION_MINIMA || minutosAlumno > DURACION_MAXIMA)) {
            throw ErrorNegocio.validacion("duracionAlumnoMin", "Escribe una duración entre 5 y 240 minutos.");
        }
        this.duracionMin = minutos;
        this.duracionAlumnoMin = minutosAlumno;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public Integer getDuracionMin() { return duracionMin; }
    public Integer getDuracionAlumnoMin() { return duracionAlumnoMin; }
}
