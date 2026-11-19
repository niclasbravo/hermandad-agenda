package cl.udd.hermandad.agenda.pacientes.dominio;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RF-41. El archivo vive fuera de la base; aquí solo su ubicación. */
@Entity
@Table(name = "documento_adjunto")
public class DocumentoAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoAdjunto tipo;

    @Column(name = "nombre_archivo", nullable = false, length = 150)
    private String nombreArchivo;

    @Column(name = "tipo_mime", nullable = false, length = 60)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private Integer tamanoBytes;

    @Column(name = "ruta_almacenamiento", nullable = false, length = 255)
    private String rutaAlmacenamiento;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_verificacion")
    private VerificacionRequisitos verificacion;

    protected DocumentoAdjunto() {
    }

    public DocumentoAdjunto(Paciente paciente, TipoAdjunto tipo, String nombreArchivo, String tipoMime, int tamanoBytes,
                            String rutaAlmacenamiento, LocalDateTime ahora) {
        this.paciente = paciente;
        this.tipo = tipo;
        String nombre = nombreArchivo == null || nombreArchivo.isBlank() ? "documento" : nombreArchivo.replaceAll("[\\\\/]", "_");
        this.nombreArchivo = nombre.length() > 150 ? nombre.substring(nombre.length() - 150) : nombre;
        this.tipoMime = tipoMime;
        this.tamanoBytes = tamanoBytes;
        this.rutaAlmacenamiento = rutaAlmacenamiento;
        this.fechaCarga = ahora;
    }

    public void ligarA(VerificacionRequisitos verificacion) {
        this.verificacion = verificacion;
    }

    public Integer getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public TipoAdjunto getTipo() { return tipo; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getTipoMime() { return tipoMime; }
    public Integer getTamanoBytes() { return tamanoBytes; }
    public String getRutaAlmacenamiento() { return rutaAlmacenamiento; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public VerificacionRequisitos getVerificacion() { return verificacion; }
}
