package cl.udd.hermandad.agenda.pacientes.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.seguridad.dominio.Usuario;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** RF-13, RN-01. Se guarda cada verificación; vale la más reciente. */
@Entity
@Table(name = "verificacion_requisitos")
public class VerificacionRequisitos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_verificacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_verificacion", nullable = false)
    private LocalDate fechaVerificacion;

    @Column(name = "cesfam_inscripcion", nullable = false, length = 100)
    private String cesfamInscripcion;

    @Column(name = "fonasa_vigente", nullable = false)
    private boolean fonasaVigente;

    @Column(name = "inscrito_cesfam", nullable = false)
    private boolean inscritoCesfam;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false)
    private ResultadoVerificacion resultado;

    @Column(name = "observacion", length = 300)
    private String observacion;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected VerificacionRequisitos() {
    }

    public VerificacionRequisitos(Paciente paciente, Usuario usuario, LocalDate fechaVerificacion, String cesfamInscripcion,
                                  boolean fonasaVigente, boolean inscritoCesfam, String observacion, LocalDateTime ahora) {
        if (cesfamInscripcion == null || cesfamInscripcion.isBlank()) {
            throw ErrorNegocio.validacion("cesfamInscripcion", "Escribe el CESFAM de inscripción.");
        }
        if (fechaVerificacion == null || fechaVerificacion.isAfter(ahora.toLocalDate())) {
            throw ErrorNegocio.validacion("fechaVerificacion", "Indica una fecha de verificación válida (hoy o antes).");
        }
        this.paciente = paciente;
        this.usuario = usuario;
        this.fechaVerificacion = fechaVerificacion;
        this.cesfamInscripcion = cesfamInscripcion.trim();
        this.fonasaVigente = fonasaVigente;
        this.inscritoCesfam = inscritoCesfam;
        this.observacion = observacion == null || observacion.isBlank() ? null : observacion.trim();
        this.resultado = calcularResultado(fonasaVigente, inscritoCesfam);
        this.fechaRegistro = ahora;
    }

    /** HABILITADO solo si ambas casillas están marcadas. */
    public static ResultadoVerificacion calcularResultado(boolean fonasaVigente, boolean inscritoCesfam) {
        return fonasaVigente && inscritoCesfam ? ResultadoVerificacion.HABILITADO : ResultadoVerificacion.NO_CUMPLE;
    }

    public ResultadoVerificacion calcularResultado() {
        return calcularResultado(fonasaVigente, inscritoCesfam);
    }

    public Integer getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public Usuario getUsuario() { return usuario; }
    public LocalDate getFechaVerificacion() { return fechaVerificacion; }
    public String getCesfamInscripcion() { return cesfamInscripcion; }
    public boolean isFonasaVigente() { return fonasaVigente; }
    public boolean isInscritoCesfam() { return inscritoCesfam; }
    public ResultadoVerificacion getResultado() { return resultado; }
    public String getObservacion() { return observacion; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
