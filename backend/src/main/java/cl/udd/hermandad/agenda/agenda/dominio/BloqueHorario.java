package cl.udd.hermandad.agenda.agenda.dominio;

import cl.udd.hermandad.agenda.catalogo.dominio.Box;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesional;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** RF-04, RN-03, D-09. Nunca se borra: se desactiva (D-17). */
@Entity
@Table(name = "bloque_horario")
public class BloqueHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bloque")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profesional", nullable = false)
    private Profesional profesional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_box", nullable = false, columnDefinition = "smallint unsigned")
    private Box box;

    /** 1 = lunes … 7 = domingo. */
    @Column(name = "dia_semana", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_termino", nullable = false)
    private LocalTime horaTermino;

    @Column(name = "vigencia_desde", nullable = false)
    private LocalDate vigenciaDesde;

    @Column(name = "vigencia_hasta", nullable = false)
    private LocalDate vigenciaHasta;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected BloqueHorario() {
    }

    public BloqueHorario(Profesional profesional, Box box, int diaSemana, LocalTime horaInicio, LocalTime horaTermino,
                         LocalDate vigenciaDesde, LocalDate vigenciaHasta, Integer idUsuario, LocalDateTime ahora) {
        this.profesional = profesional;
        this.box = box;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaTermino = horaTermino;
        this.vigenciaDesde = vigenciaDesde;
        this.vigenciaHasta = vigenciaHasta;
        this.idUsuario = idUsuario;
        this.fechaRegistro = ahora;
        validar();
    }

    /** CU-02 E2 y reglas de forma. */
    public void validar() {
        if (diaSemana == null || diaSemana < 1 || diaSemana > 7) {
            throw ErrorNegocio.validacion("diaSemana", "Elige un día de la semana.");
        }
        if (horaInicio == null || horaTermino == null || !horaTermino.isAfter(horaInicio)) {
            throw ErrorNegocio.validacion("horaTermino", "La hora de término debe ser posterior a la hora de inicio.");
        }
        if (vigenciaDesde == null || vigenciaHasta == null || vigenciaHasta.isBefore(vigenciaDesde)) {
            throw ErrorNegocio.validacion("vigenciaHasta", "La vigencia debe terminar después de empezar.");
        }
        if (!profesional.atiendeEn(box.getPoliclinico().getId())) {
            throw ErrorNegocio.validacion("idBox", "Este profesional no atiende en el policlínico de ese box.");
        }
    }

    /** RN-12 / D-09: mismo día, mismo profesional o box, horas que se cruzan y vigencias que se cruzan. */
    public boolean seSuperponeCon(BloqueHorario otro) {
        if (otro == this || (id != null && id.equals(otro.id)) || !activo || !otro.activo) {
            return false;
        }
        boolean mismoRecurso = profesional.getId().equals(otro.profesional.getId()) || box.getId().equals(otro.box.getId());
        boolean horasSeCruzan = horaInicio.isBefore(otro.horaTermino) && otro.horaInicio.isBefore(horaTermino);
        boolean vigenciasSeCruzan = !vigenciaDesde.isAfter(otro.vigenciaHasta) && !otro.vigenciaDesde.isAfter(vigenciaHasta);
        return diaSemana.equals(otro.diaSemana) && mismoRecurso && horasSeCruzan && vigenciasSeCruzan;
    }

    /** Horas de inicio de los cupos de un día: desde la hora de inicio, en pasos de la duración; el tramo sobrante no genera cupo. */
    public List<LocalTime> horasDelDia(Integer duracion) {
        if (duracion == null) {
            throw ErrorNegocio.regla("SIN_DURACION", "No se pueden generar cupos: " + profesional.getProfesion().getNombre()
                    + " no tiene duración definida. Configúrala en Policlínicos y profesionales.");
        }
        if (duracion <= 0) {
            throw ErrorNegocio.validacion("duracion", "La duración debe ser mayor que 0.");
        }
        List<LocalTime> horas = new ArrayList<>();
        int inicio = horaInicio.toSecondOfDay() / 60;
        int termino = horaTermino.toSecondOfDay() / 60;
        for (int m = inicio; m + duracion <= termino; m += duracion) {
            horas.add(LocalTime.of(m / 60, m % 60));
        }
        return horas;
    }

    /** Fechas de la vigencia (entre dos límites opcionales) cuyo día de la semana coincide. */
    public List<LocalDate> fechas(LocalDate desde, LocalDate hasta) {
        LocalDate inicio = desde == null || desde.isBefore(vigenciaDesde) ? vigenciaDesde : desde;
        LocalDate fin = hasta == null || hasta.isAfter(vigenciaHasta) ? vigenciaHasta : hasta;
        List<LocalDate> fechas = new ArrayList<>();
        DayOfWeek dia = DayOfWeek.of(diaSemana);
        LocalDate f = inicio;
        while (f.getDayOfWeek() != dia) {
            f = f.plusDays(1);
        }
        for (; !f.isAfter(fin); f = f.plusWeeks(1)) {
            fechas.add(f);
        }
        return fechas;
    }

    /** RF-06. Genera los cupos de toda la vigencia. */
    public List<Cupo> generarCupos(Integer duracion) {
        return generarCupos(duracion, null, null);
    }

    public List<Cupo> generarCupos(Integer duracion, LocalDate desde, LocalDate hasta) {
        List<LocalTime> horas = horasDelDia(duracion);
        List<Cupo> cupos = new ArrayList<>();
        for (LocalDate fecha : fechas(desde, hasta)) {
            for (LocalTime h : horas) {
                cupos.add(new Cupo(this, fecha, h, h.plusMinutes(duracion)));
            }
        }
        return cupos;
    }

    public void cambiarVigencia(LocalDate desde, LocalDate hasta) {
        this.vigenciaDesde = desde;
        this.vigenciaHasta = hasta;
        validar();
    }

    public void desactivar() {
        this.activo = false;
    }

    public Integer getId() { return id; }
    public Profesional getProfesional() { return profesional; }
    public Box getBox() { return box; }
    public Integer getDiaSemana() { return diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraTermino() { return horaTermino; }
    public LocalDate getVigenciaDesde() { return vigenciaDesde; }
    public LocalDate getVigenciaHasta() { return vigenciaHasta; }
    public boolean isActivo() { return activo; }
    public Integer getIdUsuario() { return idUsuario; }
}
