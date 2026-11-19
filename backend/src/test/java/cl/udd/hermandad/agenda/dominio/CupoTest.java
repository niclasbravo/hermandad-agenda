package cl.udd.hermandad.agenda.dominio;

import cl.udd.hermandad.agenda.agenda.dominio.Bloqueo;
import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.agenda.dominio.EstadoCupo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static cl.udd.hermandad.agenda.soporte.Fabrica.cupoMedicina;
import static org.assertj.core.api.Assertions.assertThat;

class CupoTest {

    private final LocalDateTime ahora = LocalDateTime.of(2026, 10, 13, 9, 35);
    private final LocalDate hoy = ahora.toLocalDate();

    @Test
    void disponibleSiEsFuturoPublicadoLibreYNoBloqueado() {
        Cupo c = cupoMedicina(hoy.plusDays(1), "10:00");
        assertThat(c.estado(ahora, false)).isEqualTo(EstadoCupo.DISPONIBLE);
        assertThat(c.estaDisponible(ahora, false)).isTrue();
    }

    @Test
    void reservadoSiTieneCitaVigente() {
        assertThat(cupoMedicina(hoy.plusDays(1), "10:00").estado(ahora, true)).isEqualTo(EstadoCupo.RESERVADO);
    }

    @Test
    void bloqueadoTienePrioridadSobreReservado() {
        Cupo c = cupoMedicina(hoy.plusDays(1), "10:00");
        c.bloquear(new Bloqueo(1, "Reunión clínica", ahora));
        assertThat(c.estado(ahora, true)).isEqualTo(EstadoCupo.BLOQUEADO);
        assertThat(c.estaDisponible(ahora, false)).isFalse();
    }

    @Test
    void caRf08_noPublicadoFueraDelPlazoYAparecesAlEntrar() {
        Cupo lejano = cupoMedicina(hoy.plusDays(8), "10:00");
        Cupo limite = cupoMedicina(hoy.plusDays(7), "10:00");
        assertThat(lejano.estado(ahora, false)).isEqualTo(EstadoCupo.NO_PUBLICADO);
        assertThat(limite.estado(ahora, false)).isEqualTo(EstadoCupo.DISPONIBLE);
        assertThat(lejano.estado(ahora.plusDays(1), false)).isEqualTo(EstadoCupo.DISPONIBLE);
    }

    @Test
    void reservadoTienePrioridadSobreNoPublicado() {
        assertThat(cupoMedicina(hoy.plusDays(20), "10:00").estado(ahora, true)).isEqualTo(EstadoCupo.RESERVADO);
    }

    @Test
    void libreCuyaHoraYaPasoEsPasado() {
        assertThat(cupoMedicina(hoy, "09:20").estado(ahora, false)).isEqualTo(EstadoCupo.PASADO);
        assertThat(cupoMedicina(hoy, "09:40").estado(ahora, false)).isEqualTo(EstadoCupo.DISPONIBLE);
    }

    @Test
    void bloqueoExigeMotivo() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Bloqueo(1, " ", ahora))
                .hasMessageContaining("motivo");
    }
}
