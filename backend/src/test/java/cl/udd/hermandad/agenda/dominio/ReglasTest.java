package cl.udd.hermandad.agenda.dominio;

import cl.udd.hermandad.agenda.agenda.dominio.ReglaReservaWeb;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.confirmaciones.dominio.ReglaRecordatorio;
import cl.udd.hermandad.agenda.pacientes.dominio.ResultadoVerificacion;
import cl.udd.hermandad.agenda.pacientes.dominio.VerificacionRequisitos;
import cl.udd.hermandad.agenda.seguridad.dominio.ParametroSistema;
import cl.udd.hermandad.agenda.seguridad.dominio.TipoDato;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ReglasTest {

    @Test
    void cuposWebEsCeilDelPorcentaje() {
        assertThat(new ReglaReservaWeb(50, null).cuposWeb(9)).isEqualTo(5);
        assertThat(new ReglaReservaWeb(10, null).cuposWeb(9)).isEqualTo(1);
        assertThat(new ReglaReservaWeb(100, null).cuposWeb(9)).isEqualTo(9);
        assertThat(new ReglaReservaWeb(0, null).cuposWeb(9)).isZero();
        assertThat(new ReglaReservaWeb(null, null).cuposWeb(9)).isZero();
    }

    @Test
    void cuposWebRepartidosDeFormaPareja() {
        List<Integer> idx = new ReglaReservaWeb(50, null).indicesWeb(9);
        assertThat(idx).containsExactly(0, 1, 3, 5, 7).doesNotHaveDuplicates();
        assertThat(new ReglaReservaWeb(33, null).indicesWeb(9)).containsExactly(0, 3, 6);
        assertThat(new ReglaReservaWeb(null, null).indicesWeb(9)).isEmpty();
    }

    @Test
    void topeNuloEsSinTope() {
        assertThat(new ReglaReservaWeb(null, null).superaTope(100)).isFalse();
        assertThat(new ReglaReservaWeb(null, 2).superaTope(1)).isFalse();
        assertThat(new ReglaReservaWeb(null, 2).superaTope(2)).isTrue();
    }

    @Test
    void recordatorioElDiaAnteriorALaHoraConfigurada() {
        ReglaRecordatorio r = new ReglaRecordatorio(LocalTime.of(10, 0), List.of(48, 4));
        LocalDateTime cita = LocalDateTime.of(2026, 10, 13, 9, 30);
        assertThat(r.momentoRecordatorio(cita, LocalDateTime.of(2026, 10, 10, 8, 0)))
                .contains(LocalDateTime.of(2026, 10, 12, 10, 0));
        assertThat(r.momentoRecordatorio(cita, LocalDateTime.of(2026, 10, 12, 11, 0))).isEmpty();
        assertThat(r.momentosRefuerzo(cita, LocalDateTime.of(2026, 10, 10, 8, 0)))
                .containsExactly(LocalDateTime.of(2026, 10, 11, 9, 30), LocalDateTime.of(2026, 10, 13, 5, 30));
        assertThat(r.momentosRefuerzo(cita, LocalDateTime.of(2026, 10, 12, 8, 0)))
                .containsExactly(LocalDateTime.of(2026, 10, 13, 5, 30));
        assertThat(new ReglaRecordatorio(null, null).momentosRefuerzo(cita, LocalDateTime.of(2026, 10, 1, 0, 0))).isEmpty();
    }

    @Test
    void verificacionHabilitaSoloConAmbasCasillas() {
        assertThat(VerificacionRequisitos.calcularResultado(true, true)).isEqualTo(ResultadoVerificacion.HABILITADO);
        assertThat(VerificacionRequisitos.calcularResultado(true, false)).isEqualTo(ResultadoVerificacion.NO_CUMPLE);
        assertThat(VerificacionRequisitos.calcularResultado(false, true)).isEqualTo(ResultadoVerificacion.NO_CUMPLE);
        assertThat(VerificacionRequisitos.calcularResultado(false, false)).isEqualTo(ResultadoVerificacion.NO_CUMPLE);
    }

    @Test
    void caRf05_duracionDeProfesion() {
        Profesion p = new Profesion("Psicología", null);
        assertThat(p.tieneDuracion()).isFalse();
        p.definirDuracion(45, null);
        assertThat(p.getDuracionMin()).isEqualTo(45);
        assertThatThrownBy(() -> p.definirDuracion(0, null)).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> p.definirDuracion(null, null)).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> p.definirDuracion(241, null)).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> p.definirDuracion(30, 3)).isInstanceOf(ErrorNegocio.class);
    }

    @Test
    void parametroValidaSegunTipo() {
        ParametroSistema pct = new ParametroSistema("PORCENTAJE_CUPOS_WEB", null, TipoDato.PORCENTAJE, "x");
        pct.cambiar("50", 1, LocalDateTime.now());
        assertThat(pct.getValor()).isEqualTo("50");
        assertThatThrownBy(() -> pct.cambiar("150", 1, LocalDateTime.now())).isInstanceOf(ErrorNegocio.class);
        pct.cambiar("", 1, LocalDateTime.now());
        assertThat(pct.getValor()).isNull();
        ParametroSistema entero = new ParametroSistema("TOPE_RESERVAS_WEB", null, TipoDato.ENTERO, "x");
        assertThatThrownBy(() -> entero.cambiar("abc", 1, LocalDateTime.now())).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> entero.cambiar("-1", 1, LocalDateTime.now())).isInstanceOf(ErrorNegocio.class);
        ParametroSistema hora = new ParametroSistema("RECORDATORIO_HORA", "10:00", TipoDato.TEXTO, "x");
        assertThatThrownBy(() -> hora.cambiar("25:99", 1, LocalDateTime.now())).isInstanceOf(ErrorNegocio.class);
        ParametroSistema refuerzos = new ParametroSistema("REFUERZOS_HORAS_ANTES", "48,4", TipoDato.TEXTO, "x");
        assertThatThrownBy(() -> refuerzos.cambiar("48,0", 1, LocalDateTime.now())).isInstanceOf(ErrorNegocio.class);
    }
}
