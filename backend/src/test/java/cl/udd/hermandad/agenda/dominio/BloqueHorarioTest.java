package cl.udd.hermandad.agenda.dominio;

import cl.udd.hermandad.agenda.agenda.dominio.BloqueHorario;
import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.catalogo.dominio.Box;
import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesion;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesional;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static cl.udd.hermandad.agenda.soporte.Fabrica.*;
import static org.assertj.core.api.Assertions.*;

class BloqueHorarioTest {

    private final LocalDate lunes = LocalDate.of(2026, 10, 12);
    private final Policlinico pol = policlinico();
    private final Box box1 = box(pol, 1);
    private final Box box2 = box(pol, 2);

    private List<String> horas(BloqueHorario b, Integer duracion) {
        return b.horasDelDia(duracion).stream().map(LocalTime::toString).toList();
    }

    @Test
    void caRf06_1_medicoDe9a10GeneraTresCupos() {
        Profesional p = profesional(profesion("Medicina", 20), pol, false);
        BloqueHorario b = bloque(p, box1, 1, "09:00", "10:00", lunes, lunes);
        assertThat(horas(b, p.duracionAtencion())).containsExactly("09:00", "09:20", "09:40");
    }

    @Test
    void caRf06_2_psicologoDe9a1030GeneraDosCuposYElSobranteNoGenera() {
        Profesional p = profesional(profesion("Psicología", 45), pol, false);
        BloqueHorario b = bloque(p, box1, 1, "09:00", "10:30", lunes, lunes);
        assertThat(horas(b, p.duracionAtencion())).containsExactly("09:00", "09:45");
    }

    @Test
    void tramoSobranteNoGeneraCupo() {
        Profesional p = profesional(profesion("Kinesiología", 30), pol, false);
        BloqueHorario b = bloque(p, box1, 1, "09:00", "10:10", lunes, lunes);
        assertThat(horas(b, 30)).containsExactly("09:00", "09:30");
    }

    @Test
    void alumnoEnPracticaUsaLaDuracionDeAlumnos() {
        Profesion med = profesion("Medicina", 20);
        med.definirDuracion(20, 30);
        Profesional alumno = profesional(med, pol, true);
        Profesional titular = profesional(med, pol, false);
        assertThat(alumno.duracionAtencion()).isEqualTo(30);
        assertThat(titular.duracionAtencion()).isEqualTo(20);
        BloqueHorario b = bloque(alumno, box1, 1, "09:00", "10:00", lunes, lunes);
        assertThat(horas(b, alumno.duracionAtencion())).containsExactly("09:00", "09:30");
    }

    @Test
    void alumnoSinDuracionDeAlumnosUsaLaDeLaProfesion() {
        Profesional alumno = profesional(profesion("Kinesiología", 30), pol, true);
        assertThat(alumno.duracionAtencion()).isEqualTo(30);
    }

    @Test
    void sinDuracionNoSeGeneranCupos() {
        Profesional p = profesional(profesion("Fonoaudiología", null), pol, false);
        BloqueHorario b = bloque(p, box1, 1, "09:00", "10:00", lunes, lunes);
        assertThat(p.duracionAtencion()).isNull();
        assertThatThrownBy(() -> b.generarCupos(p.duracionAtencion()))
                .isInstanceOf(ErrorNegocio.class).hasMessageContaining("Fonoaudiología no tiene duración definida");
    }

    @Test
    void generaCuposSoloEnLasFechasDelDiaDeLaSemana() {
        Profesional p = profesional(profesion("Medicina", 20), pol, false);
        BloqueHorario b = bloque(p, box1, 1, "09:00", "10:00", lunes, lunes.plusWeeks(2).plusDays(3));
        List<Cupo> cupos = b.generarCupos(20);
        assertThat(cupos).hasSize(9);
        assertThat(cupos).allMatch(c -> c.getFecha().getDayOfWeek().getValue() == 1);
        assertThat(cupos.get(0).getHoraTermino()).isEqualTo(LocalTime.of(9, 20));
    }

    @Test
    void horaDeTerminoDebeSerPosterior() {
        Profesional p = profesional(profesion("Medicina", 20), pol, false);
        assertThatThrownBy(() -> bloque(p, box1, 1, "10:00", "09:00", lunes, lunes))
                .isInstanceOf(ErrorNegocio.class).hasMessageContaining("posterior");
    }

    @Test
    void seSuperponeConMismoProfesionalOMismoBox() {
        Profesional p1 = profesional(profesion("Medicina", 20), pol, false);
        Profesional p2 = profesional(profesion("Medicina", 20), pol, false);
        BloqueHorario a = bloque(p1, box1, 1, "09:00", "12:00", lunes, lunes.plusMonths(3));
        BloqueHorario mismoProfOtroBox = bloque(p1, box2, 1, "11:00", "13:00", lunes, lunes.plusMonths(3));
        BloqueHorario mismoBoxOtroProf = bloque(p2, box1, 1, "08:00", "09:30", lunes, lunes.plusMonths(3));
        BloqueHorario otroDia = bloque(p1, box1, 2, "09:00", "12:00", lunes, lunes.plusMonths(3));
        BloqueHorario contiguo = bloque(p1, box1, 1, "12:00", "13:00", lunes, lunes.plusMonths(3));
        BloqueHorario otraVigencia = bloque(p1, box1, 1, "09:00", "12:00", lunes.plusMonths(4), lunes.plusMonths(5));
        BloqueHorario otroTodo = bloque(p2, box2, 1, "09:00", "12:00", lunes, lunes.plusMonths(3));
        assertThat(a.seSuperponeCon(mismoProfOtroBox)).isTrue();
        assertThat(a.seSuperponeCon(mismoBoxOtroProf)).isTrue();
        assertThat(a.seSuperponeCon(otroDia)).isFalse();
        assertThat(a.seSuperponeCon(contiguo)).isFalse();
        assertThat(a.seSuperponeCon(otraVigencia)).isFalse();
        assertThat(a.seSuperponeCon(otroTodo)).isFalse();
        assertThat(a.seSuperponeCon(a)).isFalse();
    }

    @Test
    void unBloqueInactivoNoSeSuperpone() {
        Profesional p1 = profesional(profesion("Medicina", 20), pol, false);
        BloqueHorario a = bloque(p1, box1, 1, "09:00", "12:00", lunes, lunes.plusMonths(3));
        BloqueHorario b = bloque(p1, box1, 1, "09:00", "12:00", lunes, lunes.plusMonths(3));
        b.desactivar();
        assertThat(a.seSuperponeCon(b)).isFalse();
        ReflectionTestUtils.setField(b, "activo", true);
        assertThat(a.seSuperponeCon(b)).isTrue();
    }
}
