package cl.udd.hermandad.agenda.confirmaciones.dominio;

import jakarta.persistence.*;

/** RF-19 a RF-21, RF-38. Lenguaje simple y sin información clínica (RNF-02, RNF-06). */
@Entity
@Table(name = "plantilla_mensaje")
public class PlantillaMensaje {

    public static final String CONFIRMACION = "hermandad_confirmacion";
    public static final String CONFIRMACION_PENDIENTE = "hermandad_confirmacion_pendiente";
    public static final String RECORDATORIO = "hermandad_recordatorio";
    public static final String REFUERZO = "hermandad_refuerzo";
    public static final String ANULACION = "hermandad_anulacion";
    public static final String ANULACION_POLICLINICO = "hermandad_anulacion_policlinico";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plantilla", columnDefinition = "smallint unsigned")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoPlantilla tipo;

    @Column(name = "nombre_proveedor", nullable = false, length = 80)
    private String nombreProveedor;

    @Column(name = "texto", nullable = false, length = 1000)
    private String texto;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    protected PlantillaMensaje() {
    }

    public PlantillaMensaje(TipoPlantilla tipo, String nombreProveedor, String texto) {
        this.tipo = tipo;
        this.nombreProveedor = nombreProveedor;
        this.texto = texto;
    }

    /** El recordatorio y los refuerzos llevan los botones «Asistiré» y «No podré asistir». */
    public boolean llevaBotones() {
        return tipo == TipoPlantilla.RECORDATORIO || tipo == TipoPlantilla.REFUERZO;
    }

    public Integer getId() { return id; }
    public TipoPlantilla getTipo() { return tipo; }
    public String getNombreProveedor() { return nombreProveedor; }
    public String getTexto() { return texto; }
    public boolean isActiva() { return activa; }
}
