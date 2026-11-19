package cl.udd.hermandad.agenda.dominio;

import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.citas.dominio.*;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static cl.udd.hermandad.agenda.soporte.Fabrica.*;
import static org.assertj.core.api.Assertions.*;

/** Transiciones de la sección 4.1 (cita) y 4.2 (confirmación). */
class CitaTest {

    private final LocalDateTime ahora = LocalDateTime.of(2026, 10, 12, 8, 0);
    private final Cupo cupo = cupoMedicina(LocalDate.of(2026, 10, 13), "09:00");
    private final Paciente habilitado = paciente("12345678-5", LocalDate.of(1952, 5, 1), true, ahora);
    private final Paciente sinVerificar = paciente("9876543-3", LocalDate.of(1960, 5, 1), false, ahora);

    private Cita reservada() {
        return Cita.nueva(habilitado, cupo, CanalReserva.TENS, 2, true, null, ahora);
    }

    @Test
    void tensConPacienteHabilitadoQuedaReservada() {
        Cita c = reservada();
        assertThat(c.getEstado()).isEqualTo(EstadoCita.RESERVADA);
        assertThat(c.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacion.SIN_ENVIAR);
        assertThat(c.getIdUsuarioReserva()).isEqualTo(2);
    }

    @Test
    void tensConPacienteNoHabilitadoSeRechaza() {
        assertThatThrownBy(() -> Cita.nueva(sinVerificar, cupo, CanalReserva.TENS, 2, false, null, ahora))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.REGLA_NEGOCIO);
                    assertThat(e.getCodigo()).isEqualTo("PACIENTE_NO_HABILITADO");
                });
    }

    @Test
    void enLineaSinVerificacionQuedaPendienteYLuegoReservada() {
        Cita c = Cita.nueva(sinVerificar, cupo, CanalReserva.EN_LINEA, 99, false, null, ahora);
        assertThat(c.getEstado()).isEqualTo(EstadoCita.PENDIENTE_VERIFICACION);
        assertThat(c.getIdUsuarioReserva()).isNull();
        assertThat(c.marcarVerificada()).isTrue();
        assertThat(c.getEstado()).isEqualTo(EstadoCita.RESERVADA);
        assertThat(c.marcarVerificada()).isFalse();
    }

    @Test
    void enLineaHabilitadoQuedaReservada() {
        assertThat(Cita.nueva(habilitado, cupo, CanalReserva.EN_LINEA, null, true, null, ahora).getEstado())
                .isEqualTo(EstadoCita.RESERVADA);
    }

    @Test
    void anularExigeOrigenYDejaFecha() {
        Cita c = reservada();
        assertThatThrownBy(() -> c.anular(null, null, 2, ahora)).isInstanceOf(IllegalArgumentException.class);
        c.anular(OrigenAnulacion.TENS, "Avisó", 2, ahora);
        assertThat(c.getEstado()).isEqualTo(EstadoCita.ANULADA);
        assertThat(c.getFechaAnulacion()).isEqualTo(ahora);
        assertThat(c.getOrigenAnulacion()).isEqualTo(OrigenAnulacion.TENS);
        assertThat(c.estaVigente()).isFalse();
    }

    @Test
    void pendientePuedeAnularse() {
        Cita c = Cita.nueva(sinVerificar, cupo, CanalReserva.EN_LINEA, null, false, null, ahora);
        c.anular(OrigenAnulacion.TENS, "No presentó certificados", 2, ahora);
        assertThat(c.getEstado()).isEqualTo(EstadoCita.ANULADA);
    }

    @Test
    void estadosFinalesNoAdmitenCambios() {
        Cita anulada = reservada();
        anulada.anular(OrigenAnulacion.TENS, null, 2, ahora);
        assertThatThrownBy(() -> anulada.anular(OrigenAnulacion.TENS, null, 2, ahora)).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> anulada.registrarAsistencia(true, 2, ahora.plusDays(1)))
                .hasMessageContaining("anulada");

        Cita asistida = reservada();
        asistida.registrarAsistencia(true, 2, ahora.plusDays(1));
        assertThatThrownBy(() -> asistida.registrarAsistencia(false, 2, ahora.plusDays(1))).hasMessageContaining("ya fue registrada");
        assertThatThrownBy(() -> asistida.anular(OrigenAnulacion.TENS, null, 2, ahora.plusDays(1))).isInstanceOf(ErrorNegocio.class);
    }

    @Test
    void asistenciaSoloElDiaDeLaCitaODespues() {
        Cita c = reservada();
        assertThatThrownBy(() -> c.registrarAsistencia(true, 2, ahora)).hasMessageContaining("día de la cita");
        c.registrarAsistencia(false, 2, LocalDateTime.of(2026, 10, 13, 9, 30));
        assertThat(c.getEstado()).isEqualTo(EstadoCita.INASISTIDA);
        assertThat(c.getFechaAsistencia()).isNotNull();
    }

    @Test
    void pendienteNoAdmiteAsistencia() {
        Cita c = Cita.nueva(sinVerificar, cupo, CanalReserva.EN_LINEA, null, false, null, ahora);
        assertThatThrownBy(() -> c.registrarAsistencia(true, 2, ahora.plusDays(1))).hasMessageContaining("verifica");
    }

    @Test
    void transicionesDeConfirmacion() {
        Cita c = reservada();
        c.marcarConfirmacionEnviada();
        assertThat(c.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacion.ENVIADA);
        c.marcarSinRespuesta();
        assertThat(c.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacion.SIN_RESPUESTA);
        c.confirmar();
        assertThat(c.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacion.CONFIRMADA);
        c.marcarSinRespuesta();
        assertThat(c.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacion.CONFIRMADA);

        Cita f = reservada();
        f.marcarConfirmacionNoEnviada();
        assertThat(f.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacion.NO_ENVIADA);
        f.confirmarPorTelefono();
        assertThat(f.getEstadoConfirmacion()).isEqualTo(EstadoConfirmacion.CONFIRMADA_TELEFONO);
    }

    @Test
    void requiereLlamadaParaHoyOMananaNoEnviadaOSinRespuesta() {
        Cita c = reservada();
        LocalDate hoy = LocalDate.of(2026, 10, 12);
        assertThat(c.requiereLlamada(hoy)).isFalse();
        c.marcarConfirmacionNoEnviada();
        assertThat(c.requiereLlamada(hoy)).isTrue();
        assertThat(c.requiereLlamada(hoy.plusDays(1))).isTrue();
        assertThat(c.requiereLlamada(hoy.minusDays(1))).isFalse();
        c.anular(OrigenAnulacion.TENS, null, 2, ahora);
        assertThat(c.requiereLlamada(hoy)).isFalse();
    }
}
