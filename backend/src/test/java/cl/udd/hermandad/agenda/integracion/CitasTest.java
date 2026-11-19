package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.agenda.dominio.FiltroCupos;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** M4 · CU-04. */
class CitasTest extends PruebaIntegracion {

    @Autowired AgendamientoService agendamiento;

    private List<CupoVista> disponibles(Escenario.Base b) {
        return agendamiento.consultarCupos(new FiltroCupos(b.idPoliclinico(), b.idProfesion(), null, esc.dia(), esc.dia().plusWeeks(3), false));
    }

    @Test
    void caRf14_1_soloSeMuestranCuposDisponibles() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.admin());
        jdbc.update("insert into bloqueo (id_usuario, motivo) values (?, 'Capacitación')", b.admin().idUsuario());
        jdbc.update("update cupo set id_bloqueo = (select max(id_bloqueo) from bloqueo) where id_cupo = ?", cupos.get(2));
        esc.como(b.tens());
        agendamiento.reservar(p, cupos.get(0));
        // Solo el lunes de prueba está publicado (plazo 7 días): ni reservados, ni bloqueados, ni no publicados.
        assertThat(disponibles(b)).extracting(CupoVista::id).containsExactly(cupos.get(1));
    }

    @Test
    void caRf15_1_alReservarElCupoDejaDeMostrarseYQuedaAuditado() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        var c = agendamiento.reservar(p, cupos.get(0));
        assertThat(disponibles(b)).extracting(CupoVista::id).doesNotContain(cupos.get(0));
        assertThat(texto("select estado from cita where id_cita = ?", c.getId())).isEqualTo("RESERVADA");
        assertThat(texto("select estado_confirmacion from cita where id_cita = ?", c.getId())).isEqualTo("SIN_ENVIAR");
        assertThat(contar("select count(*) from auditoria where entidad = 'cita' and accion = 'CREAR' and id_registro = ? and id_usuario = ?",
                c.getId(), b.tens().idUsuario())).isEqualTo(1);
    }

    @Test
    void caRf15_2_pacienteSinVerificacionRecibe422() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, false);
        esc.como(b.tens());
        assertThatThrownBy(() -> agendamiento.reservar(p, cupos.get(0)))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.REGLA_NEGOCIO);
                    assertThat(e.getMessage()).startsWith("Este paciente aún no tiene registrada la verificación de FONASA y CESFAM");
                });
        assertThat(contar("select count(*) from cita where id_cupo = ?", cupos.get(0))).isZero();
    }

    @Test
    void caRf16_1_serieCreaNReservasYD06MuestraLasFechasSinCupo() {
        var b = esc.base(30);
        List<Long> cupos = esc.bloque(b, "11:30", "12:00");
        int p = esc.paciente(b, true);
        int otro = esc.paciente(b, true);
        esc.como(b.tens());
        // La tercera semana ya está tomada por otra persona.
        Long tercera = esc.cuposDe(b.idProfesional(), esc.dia().plusWeeks(2)).get(0);
        reloj.fijarEn(esc.dia().plusDays(8).atTime(8, 0));
        agendamiento.reservar(otro, tercera);
        esc.fijarRelojAntesDelDia();
        var previa = agendamiento.previsualizarSerie(b.idProfesional(), cupos.get(0), 6);
        assertThat(previa.conCupo()).hasSize(4);
        assertThat(previa.sinCupo()).extracting(AgendamientoService.FechaSerie::fecha)
                .containsExactly(esc.dia().plusWeeks(2), esc.dia().plusWeeks(5));
        var citas = agendamiento.reservarSerie(p, b.idProfesional(), cupos.get(0), 6,
                previa.conCupo().stream().map(AgendamientoService.FechaSerie::fecha).toList());
        assertThat(citas).hasSize(4);
        assertThat(contar("select count(*) from cita where id_paciente = ? and id_serie is not null and estado = 'RESERVADA'", p)).isEqualTo(4);
        assertThat(contar("select cantidad_sesiones from serie_sesiones where id_paciente = ?", p)).isEqualTo(6);
        assertThat(contar("select count(*) from mensaje m join cita c on c.id_cita = m.id_cita where c.id_paciente = ? "
                + "and m.id_plantilla = (select id_plantilla from plantilla_mensaje where nombre_proveedor = 'hermandad_confirmacion')", p)).isEqualTo(4);
    }

    @Test
    void caRf17_1_anularDejaLaCitaAnuladaYElCupoDisponible() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long id = agendamiento.reservar(p, cupos.get(0)).getId();
        agendamiento.anular(id, OrigenAnulacion.TENS, "El paciente llamó para avisar");
        assertThat(jdbc.queryForMap("select estado, origen_anulacion, motivo_anulacion, id_cupo_activo, id_usuario_anulacion from cita where id_cita = ?", id))
                .containsEntry("estado", "ANULADA").containsEntry("origen_anulacion", "TENS")
                .containsEntry("motivo_anulacion", "El paciente llamó para avisar").containsEntry("id_cupo_activo", null);
        assertThat(disponibles(b)).extracting(CupoVista::id).contains(cupos.get(0));
        // El cupo se puede volver a reservar (RN-07) y la cita anulada no se borra (D-17).
        int otro = esc.paciente(b, true);
        esc.como(b.tens());
        agendamiento.reservar(otro, cupos.get(0));
        assertThat(contar("select count(*) from cita where id_cupo = ?", cupos.get(0))).isEqualTo(2);
        assertThatThrownBy(() -> agendamiento.anular(id, OrigenAnulacion.TENS, null)).isInstanceOf(ErrorNegocio.class);
    }

    @Test
    void d11_reagendarAnulaYReservaEnUnaOperacion() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long id = agendamiento.reservar(p, cupos.get(0)).getId();
        long nueva = agendamiento.reagendar(id, cupos.get(2)).getId();
        assertThat(texto("select estado from cita where id_cita = ?", id)).isEqualTo("ANULADA");
        assertThat(texto("select motivo_anulacion from cita where id_cita = ?", id)).isEqualTo("Reagendada");
        assertThat(contar("select id_cupo from cita where id_cita = ?", nueva)).isEqualTo(cupos.get(2));
        // Si el cupo nuevo está tomado, no se anula la original.
        int otro = esc.paciente(b, true);
        esc.como(b.tens());
        agendamiento.reservar(otro, cupos.get(1));
        assertThatThrownBy(() -> agendamiento.reagendar(nueva, cupos.get(1))).isInstanceOf(ErrorNegocio.class);
        assertThat(texto("select estado from cita where id_cita = ?", nueva)).isEqualTo("RESERVADA");
    }
}
