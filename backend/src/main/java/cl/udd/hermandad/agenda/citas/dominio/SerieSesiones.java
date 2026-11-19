package cl.udd.hermandad.agenda.citas.dominio;

import cl.udd.hermandad.agenda.catalogo.dominio.Profesional;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** RF-16, RN-11. Cada sesión es una cita propia; la serie las agrupa. Una inasistida no se repone sola. */
@Entity
@Table(name = "serie_sesiones")
public class SerieSesiones {

    public static final int MINIMO = 2;
    public static final int MAXIMO = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_serie")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profesional", nullable = false)
    private Profesional profesional;

    @Column(name = "cantidad_sesiones", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer cantidadSesiones;

    @Column(name = "dia_semana", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer diaSemana;

    @Column(name = "hora", nullable = false)
    private LocalTime hora;

    @Column(name = "fecha_primera", nullable = false)
    private LocalDate fechaPrimera;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected SerieSesiones() {
    }

    public SerieSesiones(Paciente paciente, Profesional profesional, int cantidad, LocalDate fechaPrimera, LocalTime hora,
                         Integer idUsuario, LocalDateTime ahora) {
        validarCantidad(cantidad);
        this.paciente = paciente;
        this.profesional = profesional;
        this.cantidadSesiones = cantidad;
        this.fechaPrimera = fechaPrimera;
        this.diaSemana = fechaPrimera.getDayOfWeek().getValue();
        this.hora = hora;
        this.idUsuario = idUsuario;
        this.fechaRegistro = ahora;
    }

    public static void validarCantidad(int cantidad) {
        if (cantidad < MINIMO || cantidad > MAXIMO) {
            throw ErrorNegocio.validacion("cantidad", "La serie debe tener entre 2 y 30 sesiones.");
        }
    }

    /** Fechas semanales: primera fecha + 7 días × i. */
    public static List<LocalDate> fechas(LocalDate primera, int cantidad) {
        validarCantidad(cantidad);
        List<LocalDate> fechas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            fechas.add(primera.plusWeeks(i));
        }
        return fechas;
    }

    public Integer getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public Profesional getProfesional() { return profesional; }
    public Integer getCantidadSesiones() { return cantidadSesiones; }
    public Integer getDiaSemana() { return diaSemana; }
    public LocalTime getHora() { return hora; }
    public LocalDate getFechaPrimera() { return fechaPrimera; }
}
