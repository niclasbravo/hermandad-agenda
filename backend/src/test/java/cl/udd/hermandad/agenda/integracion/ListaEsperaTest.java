package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import cl.udd.hermandad.agenda.listaespera.dominio.InscripcionEspera;
import cl.udd.hermandad.agenda.listaespera.dominio.MotivoRetiro;
import cl.udd.hermandad.agenda.reportes.aplicacion.ReporteService;
import cl.udd.hermandad.agenda.reportes.dominio.FiltroReporte;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M5 · lista de espera (RF-25, RF-26, RF-42, RF-43). */
class ListaEsperaTest extends PruebaIntegracion {

    @Autowired ListaEsperaService lista;
    @Autowired AgendamientoService agendamiento;
    @Autowired ReporteService reportes;
    @Autowired MockMvc mvc;

    /** Los endpoints devuelven los movimientos: deben cargarse dentro de la transacción (open-in-view desactivado). */
    @Test
    void listarAdelantarYRetirarPorHttpRespondenConLosMovimientos() throws Exception {
        var b = esc.base();
        int p1 = esc.paciente(b, true);
        int p2 = esc.paciente(b, true);
        esc.como(b.tens());
        lista.inscribir(p1, b.idPoliclinico(), b.idProfesion());
        reloj.fijarEn(reloj.ahora().plusMinutes(5));
        var i2 = lista.inscribir(p2, b.idPoliclinico(), b.idProfesion());
        var tens = authentication(Escenario.autenticacion(b.tens()));

        mvc.perform(get("/api/lista-espera").param("profesion", String.valueOf(b.idProfesion())).with(tens))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movimientos[0].tipo").value("INSCRIPCION"));
        mvc.perform(post("/api/lista-espera/" + i2.getId() + "/adelantar").with(csrf()).with(tens)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Urgencia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adelantada").value(true));
        mvc.perform(post("/api/lista-espera/" + i2.getId() + "/retirar").with(csrf()).with(tens)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"DESISTE\"}"))
                .andExpect(status().isOk());
        assertThat(texto("select estado from inscripcion_espera where id_inscripcion = ?", i2.getId())).isEqualTo("RETIRADA");
        // Un segundo intento (doble clic) avisa que ya no está, sin error interno.
        mvc.perform(post("/api/lista-espera/" + i2.getId() + "/retirar").with(csrf()).with(tens)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"DESISTE\"}"))
                .andExpect(jsonPath("$.codigo").value("INSCRIPCION_RETIRADA"));
    }

    @Test
    void caRf25_1_apareceConSuFechaPorOrdenDeLlegadaYConMovimientoInscripcion() {
        var b = esc.base();
        int p1 = esc.paciente(b, true);
        int p2 = esc.paciente(b, true);
        esc.como(b.tens());
        var i1 = lista.inscribir(p1, b.idPoliclinico(), b.idProfesion());
        reloj.fijarEn(reloj.ahora().plusMinutes(5));
        lista.inscribir(p2, b.idPoliclinico(), b.idProfesion());
        assertThat(lista.listar(b.idPoliclinico(), b.idProfesion())).extracting(i -> i.getPaciente().getId()).containsExactly(p1, p2);
        assertThat(contar("select count(*) from movimiento_espera where id_inscripcion = ? and tipo = 'INSCRIPCION'", i1.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select fecha_inscripcion from inscripcion_espera where id_inscripcion = ?", java.time.LocalDateTime.class, i1.getId()))
                .isEqualTo(esc.dia().minusDays(1).atTime(8, 0));
        assertThatThrownBy(() -> lista.inscribir(p1, b.idPoliclinico(), b.idProfesion())).isInstanceOf(ErrorNegocio.class);
    }

    @Test
    void caRf26_1_alLiberarseUnCupoSeAvisaConLaPrimeraDeLaLista() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int reservado = esc.paciente(b, true);
        int esperando = esc.paciente(b, true);
        esc.como(b.tens());
        lista.inscribir(esperando, b.idPoliclinico(), b.idProfesion());
        long cita = agendamiento.reservar(reservado, cupos.get(1)).getId();
        agendamiento.anular(cita, OrigenAnulacion.TENS, null);
        var avisos = lista.avisosPendientes(b.idPoliclinico());
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).cupo().getId()).isEqualTo(cupos.get(1));
        assertThat(avisos.get(0).primera().getPaciente().getId()).isEqualTo(esperando);
        // El sistema no asigna el cupo solo.
        assertThat(contar("select count(*) from cita where id_cupo_activo = ?", cupos.get(1))).isZero();
        lista.atenderAviso(avisos.get(0).aviso().getId());
        assertThat(lista.avisosPendientes(b.idPoliclinico())).isEmpty();
        assertThat(contar("select id_usuario from aviso_cupo_liberado where id_cita_anulada = ?", cita)).isEqualTo(b.tens().idUsuario().longValue());
    }

    @Test
    void sinPacientesEnEsperaNoSeCreaAviso() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long cita = agendamiento.reservar(p, cupos.get(0)).getId();
        agendamiento.anular(cita, OrigenAnulacion.TENS, null);
        assertThat(contar("select count(*) from aviso_cupo_liberado where id_cita_anulada = ?", cita)).isZero();
    }

    @Test
    void caRf42_1_adelantarExigeMotivoYRegistraQuienCuandoYPorQue() {
        var b = esc.base();
        int p1 = esc.paciente(b, true), p2 = esc.paciente(b, true), p3 = esc.paciente(b, true);
        esc.como(b.tens());
        lista.inscribir(p1, b.idPoliclinico(), b.idProfesion());
        reloj.fijarEn(reloj.ahora().plusMinutes(1));
        lista.inscribir(p2, b.idPoliclinico(), b.idProfesion());
        reloj.fijarEn(reloj.ahora().plusMinutes(1));
        InscripcionEspera tercera = lista.inscribir(p3, b.idPoliclinico(), b.idProfesion());
        assertThatThrownBy(() -> lista.adelantar(tercera.getId(), " ", 1)).isInstanceOf(ErrorNegocio.class);
        lista.adelantar(tercera.getId(), "Adulta mayor con indicación médica", null);
        assertThat(lista.listar(b.idPoliclinico(), b.idProfesion())).extracting(i -> i.getPaciente().getId()).containsExactly(p3, p1, p2);
        assertThat(jdbc.queryForMap("select motivo, id_usuario from movimiento_espera where id_inscripcion = ? and tipo = 'ADELANTO'", tercera.getId()))
                .containsEntry("motivo", "Adulta mayor con indicación médica").containsEntry("id_usuario", (long) b.tens().idUsuario());
        assertThat(contar("select count(*) from auditoria where accion = 'ADELANTAR' and id_registro = ?", tercera.getId())).isEqualTo(1);
    }

    @Test
    void rf43_retirarConMotivoDejaRetiradaYNoCuentaEnLaDemanda() {
        var b = esc.base();
        int p1 = esc.paciente(b, true), p2 = esc.paciente(b, true);
        esc.como(b.tens());
        var i1 = lista.inscribir(p1, b.idPoliclinico(), b.idProfesion());
        var i2 = lista.inscribir(p2, b.idPoliclinico(), b.idProfesion());
        assertThatThrownBy(() -> lista.retirar(i2.getId(), MotivoRetiro.OTRO, null, null)).isInstanceOf(ErrorNegocio.class);
        lista.retirar(i2.getId(), MotivoRetiro.OTRO, "Se llamó tres veces", null);
        assertThat(texto("select estado from inscripcion_espera where id_inscripcion = ?", i2.getId())).isEqualTo("RETIRADA");
        assertThat(texto("select motivo from movimiento_espera where id_inscripcion = ? and tipo = 'RETIRO'", i2.getId()))
                .isEqualTo("Otro motivo: Se llamó tres veces");
        esc.como(b.admin());
        var demanda = reportes.demanda(new FiltroReporte(esc.dia(), esc.dia(), b.idPoliclinico(), null));
        assertThat(demanda.enEspera()).isEqualTo(1);
        assertThat(i1.getId()).isNotNull();
    }
}
