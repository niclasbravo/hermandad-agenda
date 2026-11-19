package cl.udd.hermandad.agenda.catalogo.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import jakarta.persistence.*;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** RF-02, RF-03, RF-08. Nunca se borra: se desactiva (D-17). */
@Entity
@Table(name = "profesional")
public class Profesional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_profesional")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_profesion", nullable = false, columnDefinition = "smallint unsigned")
    private Profesion profesion;

    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 80)
    private String apellidos;

    @Column(name = "es_alumno_practica", nullable = false)
    private boolean esAlumnoPractica;

    @Embedded
    private Credencial credencial;

    @Column(name = "plazo_publicacion_dias", nullable = false, columnDefinition = "smallint unsigned")
    private Integer plazoPublicacionDias = 7;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @ManyToMany
    @JoinTable(name = "profesional_policlinico",
            joinColumns = @JoinColumn(name = "id_profesional"),
            inverseJoinColumns = @JoinColumn(name = "id_policlinico", columnDefinition = "smallint unsigned"))
    private Set<Policlinico> policlinicos = new LinkedHashSet<>();

    protected Profesional() {
    }

    public Profesional(Profesion profesion, String nombres, String apellidos, boolean esAlumnoPractica,
                       Credencial credencial, Set<Policlinico> policlinicos) {
        actualizar(profesion, nombres, apellidos, esAlumnoPractica, credencial, policlinicos);
    }

    public void actualizar(Profesion profesion, String nombres, String apellidos, boolean esAlumnoPractica,
                           Credencial credencial, Set<Policlinico> policlinicos) {
        if (policlinicos == null || policlinicos.isEmpty()) {
            throw ErrorNegocio.validacion("idsPoliclinico", "Elige al menos un policlínico donde atiende.");
        }
        this.profesion = profesion;
        this.nombres = nombres.trim();
        this.apellidos = apellidos.trim();
        this.esAlumnoPractica = esAlumnoPractica;
        this.credencial = credencial;
        this.policlinicos.clear();
        this.policlinicos.addAll(policlinicos);
    }

    /** D-08: duración de la atención; NULL si la profesión no tiene duración (no se generan cupos). */
    public Integer duracionAtencion() {
        return profesion.duracionPara(esAlumnoPractica);
    }

    public void desactivar() {
        this.activo = false;
    }

    public void configurarPublicacion(int dias) {
        if (dias < 1 || dias > 180) {
            throw ErrorNegocio.validacion("plazoPublicacionDias", "El plazo debe estar entre 1 y 180 días.");
        }
        this.plazoPublicacionDias = dias;
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    public boolean atiendeEn(Integer idPoliclinico) {
        return policlinicos.stream().anyMatch(p -> p.getId().equals(idPoliclinico));
    }

    public List<Policlinico> policlinicosOrdenados() {
        return policlinicos.stream().sorted(Comparator.comparing(Policlinico::getId)).toList();
    }

    public Integer getId() { return id; }
    public Profesion getProfesion() { return profesion; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public boolean isEsAlumnoPractica() { return esAlumnoPractica; }
    public Credencial getCredencial() { return credencial; }
    public Integer getPlazoPublicacionDias() { return plazoPublicacionDias; }
    public boolean isActivo() { return activo; }
    public Set<Policlinico> getPoliclinicos() { return policlinicos; }
}
