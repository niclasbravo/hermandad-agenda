package cl.udd.hermandad.agenda.pacientes.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Textos;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** RF-09 a RF-12, RF-36. Sin información clínica (RES-08). */
@Entity
@Table(name = "paciente")
public class Paciente {

    public enum Destinatario { PACIENTE, ADULTO_RESPONSABLE }

    public record TelefonoMensajeria(Destinatario destinatario, String telefono) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paciente")
    private Integer id;

    @Embedded
    private DocumentoIdentidad documento;

    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 80)
    private String apellidos;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "telefono_celular", nullable = false, length = 20)
    private String telefonoCelular;

    @Column(name = "correo", length = 120)
    private String correo;

    @Column(name = "direccion", nullable = false, length = 150)
    private String direccion;

    @Column(name = "genero", nullable = false, length = 30)
    private String genero;

    @Column(name = "nacionalidad", nullable = false, length = 60)
    private String nacionalidad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_adulto_responsable")
    private AdultoResponsable adultoResponsable;

    @Column(name = "relacion_adulto", length = 40)
    private String relacionAdulto;

    @Column(name = "fecha_aviso_mensajes", nullable = false)
    private LocalDateTime fechaAvisoMensajes;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_registro", nullable = false)
    private CanalRegistro canalRegistro;

    @Column(name = "id_usuario_registro")
    private Integer idUsuarioRegistro;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @OneToMany(mappedBy = "paciente")
    private List<VerificacionRequisitos> verificaciones = new ArrayList<>();

    protected Paciente() {
    }

    public Paciente(DocumentoIdentidad documento, CanalRegistro canal, Integer idUsuarioRegistro, LocalDateTime ahora) {
        if (canal == CanalRegistro.TENS && idUsuarioRegistro == null) {
            throw new IllegalArgumentException("El registro por la TENS/TONS requiere el usuario");
        }
        this.documento = documento;
        this.canalRegistro = canal;
        this.idUsuarioRegistro = idUsuarioRegistro;
        this.fechaRegistro = ahora;
        // A-01: se informa que los mensajes por WhatsApp son obligatorios y se guarda cuándo.
        this.fechaAvisoMensajes = ahora;
    }

    /** Actualiza los datos de ingreso. RN-05: si es menor, exige adulto responsable. */
    public void actualizarDatos(String nombres, String apellidos, LocalDate fechaNacimiento, String telefonoCelular,
                                String correo, String direccion, String genero, String nacionalidad,
                                AdultoResponsable adulto, String relacion, LocalDate hoy) {
        requerido("nombres", nombres, "Escribe los nombres.");
        requerido("apellidos", apellidos, "Escribe los apellidos.");
        requerido("direccion", direccion, "Escribe la dirección.");
        requerido("genero", genero, "Elige el género.");
        requerido("nacionalidad", nacionalidad, "Escribe la nacionalidad.");
        if (fechaNacimiento == null || fechaNacimiento.isAfter(hoy) || fechaNacimiento.isBefore(hoy.minusYears(120))) {
            throw ErrorNegocio.validacion("fechaNacimiento", "Escribe una fecha de nacimiento válida.");
        }
        if (correo != null && !correo.isBlank() && !correo.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ErrorNegocio.validacion("correo", "Escribe un correo válido o déjalo vacío.");
        }
        this.nombres = nombres.trim();
        this.apellidos = apellidos.trim();
        this.fechaNacimiento = fechaNacimiento;
        this.telefonoCelular = Telefono.normalizar("telefonoCelular", telefonoCelular);
        this.correo = correo == null || correo.isBlank() ? null : correo.trim();
        this.direccion = direccion.trim();
        this.genero = genero.trim();
        this.nacionalidad = nacionalidad.trim();
        if (adulto != null) {
            requerido("adulto.relacion", relacion, "Indica la relación del adulto con el paciente.");
            this.adultoResponsable = adulto;
            this.relacionAdulto = relacion.trim();
        } else {
            this.adultoResponsable = null;
            this.relacionAdulto = null;
        }
        if (esMenor(hoy) && adultoResponsable == null) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.REGLA_NEGOCIO, "MENOR_SIN_ADULTO",
                    "Para registrar a un menor de edad se necesitan los datos de un adulto responsable.");
        }
    }

    private static void requerido(String campo, String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw ErrorNegocio.validacion(campo, mensaje);
        }
    }

    public int edad(LocalDate hoy) {
        return Period.between(fechaNacimiento, hoy).getYears();
    }

    public boolean esMenor(LocalDate hoy) {
        return edad(hoy) < 18;
    }

    public Optional<VerificacionRequisitos> verificacionVigente() {
        return verificaciones.stream()
                .max(Comparator.comparing(VerificacionRequisitos::getFechaVerificacion)
                        .thenComparing(v -> v.getId() == null ? Integer.MAX_VALUE : v.getId()));
    }

    /** RN-01: vale la verificación más reciente. */
    public boolean estaHabilitado() {
        return verificacionVigente().map(v -> v.getResultado() == ResultadoVerificacion.HABILITADO).orElse(false);
    }

    public void agregarVerificacion(VerificacionRequisitos v) {
        verificaciones.add(v);
    }

    /** D-07: los mensajes van al paciente y, si tiene, a su adulto responsable. */
    public List<TelefonoMensajeria> telefonosMensajeria() {
        List<TelefonoMensajeria> lista = new ArrayList<>();
        lista.add(new TelefonoMensajeria(Destinatario.PACIENTE, telefonoCelular));
        if (adultoResponsable != null) {
            lista.add(new TelefonoMensajeria(Destinatario.ADULTO_RESPONSABLE, adultoResponsable.getTelefono()));
        }
        return lista;
    }

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    public String nombrePila() {
        return Textos.primerNombre(nombres);
    }

    public String iniciales() {
        String a = apellidos == null || apellidos.isBlank() ? "" : apellidos.substring(0, 1);
        return (nombres.substring(0, 1) + a).toUpperCase();
    }

    public Integer getId() { return id; }
    public DocumentoIdentidad getDocumento() { return documento; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public String getTelefonoCelular() { return telefonoCelular; }
    public String getCorreo() { return correo; }
    public String getDireccion() { return direccion; }
    public String getGenero() { return genero; }
    public String getNacionalidad() { return nacionalidad; }
    public AdultoResponsable getAdultoResponsable() { return adultoResponsable; }
    public String getRelacionAdulto() { return relacionAdulto; }
    public LocalDateTime getFechaAvisoMensajes() { return fechaAvisoMensajes; }
    public CanalRegistro getCanalRegistro() { return canalRegistro; }
    public Integer getIdUsuarioRegistro() { return idUsuarioRegistro; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public List<VerificacionRequisitos> getVerificaciones() { return verificaciones; }
}
