package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.asistencia.aplicacion.AsistenciaService;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import cl.udd.hermandad.agenda.reportes.aplicacion.ReporteService;
import cl.udd.hermandad.agenda.reportes.dominio.FiltroReporte;
import cl.udd.hermandad.agenda.reportes.dominio.ReporteCaracterizacion;
import org.junit.jupiter.api.BeforeEach;
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

/** M7 · CU-06 y M8 · CU-07 sobre un conjunto de datos conocido. */
class AsistenciaYReportesTest extends PruebaIntegracion {

    @Autowired AsistenciaService asistencia;
    @Autowired AgendamientoService agendamiento;
    @Autowired NotificacionService notificaciones;
    @Autowired ListaEsperaService lista;
    @Autowired ReporteService reportes;
    @Autowired MockMvc mvc;

    /** 6 cupos; cupo 6 bloqueado; A, B y C reservan (cupos 1 a 3); D reserva el cupo 4 y anula; E espera en la lista. */
    record Datos(Escenario.Base b, List<Long> cupos, long citaA, long citaB, long citaC, long citaD, int a) {
    }

    @BeforeEach
    void categorias() {
        if (contar("select count(*) from categoria_etaria") == 0) {
            jdbc.update("insert into categoria_etaria (nombre, edad_desde, edad_hasta) values ('0 a 17 años',0,17),('18 a 59 años',18,59),('60 años o más',60,null)");
        }
    }

    private Datos datos() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "11:00");
        int a = esc.paciente(b, true), bb = esc.paciente(b, true), c = esc.paciente(b, true), d = esc.paciente(b, true), e = esc.paciente(b, true);
        esc.como(b.tens());
        long ca = agendamiento.reservar(a, cupos.get(0)).getId();
        long cb = agendamiento.reservar(bb, cupos.get(1)).getId();
        long cc = agendamiento.reservar(c, cupos.get(2)).getId();
        long cd = agendamiento.reservar(d, cupos.get(3)).getId();
        agendamiento.anular(cd, OrigenAnulacion.TENS, null);
        lista.inscribir(e, b.idPoliclinico(), b.idProfesion());
        jdbc.update("insert into bloqueo (id_usuario, motivo) values (?, 'Reunión clínica')", b.admin().idUsuario());
        jdbc.update("update cupo set id_bloqueo = (select max(id_bloqueo) from bloqueo) where id_cupo = ?", cupos.get(5));
        return new Datos(b, cupos, ca, cb, cc, cd, a);
    }

    private FiltroReporte filtro(Datos d) {
        return new FiltroReporte(esc.dia(), esc.dia(), d.b().idPoliclinico(), null);
    }

    @Test
    void asistenciaSinIndicarSiAsistioSeRechazaYNoCambiaLaCita() throws Exception {
        var d = datos();
        mvc.perform(post("/api/citas/" + d.citaA() + "/asistencia").with(csrf())
                        .with(authentication(Escenario.autenticacion(d.b().tens())))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(texto("select estado from cita where id_cita = ?", d.citaA())).isEqualTo("RESERVADA");
    }

    @Test
    void caRf18_1_agendaDelDiaMuestraLasCitasConSuConfirmacion() throws Exception {
        var d = datos();
        jdbc.update("update cita set estado_confirmacion = 'NO_ENVIADA' where id_cita = ?", d.citaB());
        mvc.perform(get("/api/agenda-dia").param("fecha", esc.dia().toString()).with(authentication(Escenario.autenticacion(d.b().tens()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citas.length()").value(3))
                .andExpect(jsonPath("$.citas[0].cita.horaInicio").value("09:00"))
                .andExpect(jsonPath("$.citas[1].cita.estadoConfirmacion").value("NO_ENVIADA"))
                .andExpect(jsonPath("$.citas[1].requiereLlamada").value(true))
                .andExpect(jsonPath("$.resumen.citasHoy").value(3))
                .andExpect(jsonPath("$.resumen.porLlamar").value(1))
                .andExpect(jsonPath("$.resumen.enListaEspera").value(1));
        mvc.perform(get("/api/agenda-dia").param("fecha", esc.dia().toString()).param("vista", "POR_PROFESIONAL")
                        .with(authentication(Escenario.autenticacion(d.b().tens()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columnas[0].cupos.length()").value(6));
    }

    @Test
    void caRf22_1_asistenciaUnaSolaVezYSeReflejaSinTraspaso() {
        var d = datos();
        esc.como(d.b().tens());
        assertThatThrownBy(() -> asistencia.registrarAsistencia(d.citaA(), true))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("ASISTENCIA_ANTICIPADA"));
        reloj.fijarEn(esc.dia().atTime(12, 0));
        asistencia.registrarAsistencia(d.citaA(), true);
        assertThatThrownBy(() -> asistencia.registrarAsistencia(d.citaA(), false))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("ASISTENCIA_REGISTRADA"));
        assertThatThrownBy(() -> asistencia.registrarAsistencia(d.citaD(), true))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("CITA_ANULADA"));
        assertThat(agendamiento.historial(d.a()).get(0).getEstado().name()).isEqualTo("ASISTIDA");
        assertThat(contar("select count(*) from auditoria where entidad = 'cita' and id_registro = ? and detalle = 'Asistió'", d.citaA())).isEqualTo(1);
    }

    @Test
    void caRf27_1_ocupacionCoincideConLosRegistros() {
        var d = datos();
        esc.como(d.b().admin());
        var r = reportes.ocupacion(filtro(d));
        assertThat(r.ofrecidos()).isEqualTo(5);
        assertThat(r.reservados()).isEqualTo(3);
        assertThat(r.disponibles()).isEqualTo(2);
        assertThat(r.porcentajeOcupacion()).isEqualTo(60.0);
    }

    @Test
    void caRf28_1_y_rnf12_asistenciaSeCalculaEnVivo() {
        var d = datos();
        esc.como(d.b().admin());
        var antes = reportes.asistencia(filtro(d)).total();
        assertThat(antes.reservadas()).isEqualTo(3);
        assertThat(antes.inasistencias()).isZero();
        reloj.fijarEn(esc.dia().atTime(12, 0));
        esc.como(d.b().tens());
        asistencia.registrarAsistencia(d.citaA(), true);
        asistencia.registrarAsistencia(d.citaB(), false);
        esc.como(d.b().admin());
        var despues = reportes.asistencia(filtro(d)).total();
        assertThat(despues.asistidas()).isEqualTo(1);
        assertThat(despues.inasistencias()).isEqualTo(1);
        assertThat(despues.porcentajeInasistencia()).isEqualTo(50.0);
        assertThat(reportes.asistencia(filtro(d)).evolucion()).hasSize(6);
    }

    @Test
    void caRf29_1_demandaEsReservasMasListaDeEspera() {
        var d = datos();
        esc.como(d.b().admin());
        var r = reportes.demanda(filtro(d));
        var fila = r.filas().stream().filter(f -> f.idProfesional() == d.b().idProfesional()).findFirst().orElseThrow();
        assertThat(fila.reservas()).isEqualTo(3);
        assertThat(fila.enEspera()).isEqualTo(1);
        assertThat(fila.demanda()).isEqualTo(4);
    }

    @Test
    void caRf30_1_lasCategoriasSumanElTotal() {
        var d = datos();
        reloj.fijarEn(esc.dia().atTime(12, 0));
        esc.como(d.b().tens());
        asistencia.registrarAsistencia(d.citaA(), true);
        asistencia.registrarAsistencia(d.citaB(), true);
        esc.como(d.b().admin());
        ReporteCaracterizacion r = reportes.caracterizacion(filtro(d));
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.porEdad().stream().mapToLong(ReporteCaracterizacion.Categoria::cantidad).sum()).isEqualTo(2);
        assertThat(r.porGenero().stream().mapToLong(ReporteCaracterizacion.Categoria::cantidad).sum()).isEqualTo(2);
        assertThat(r.porNacionalidad().stream().mapToLong(ReporteCaracterizacion.Categoria::cantidad).sum()).isEqualTo(2);
        assertThat(r.adultos() + r.ninos()).isEqualTo(2);
    }

    @Test
    void caRf24_1_inasistenciaReiteradaSoloMuestraLosQueAlcanzanElUmbralYLaTensVeSuPoliclinico() throws Exception {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "11:00");
        int muchas = esc.paciente(b, true), pocas = esc.paciente(b, true);
        esc.como(b.tens());
        List<Long> citas = List.of(agendamiento.reservar(muchas, cupos.get(0)).getId(), agendamiento.reservar(muchas, cupos.get(1)).getId(),
                agendamiento.reservar(muchas, cupos.get(2)).getId(), agendamiento.reservar(pocas, cupos.get(3)).getId(),
                agendamiento.reservar(pocas, cupos.get(4)).getId());
        reloj.fijarEn(esc.dia().atTime(12, 0));
        citas.forEach(c -> asistencia.registrarAsistencia(c, false));
        var r = reportes.inasistenciaReiterada(new FiltroReporte(esc.dia(), esc.dia(), b.idPoliclinico(), null));
        assertThat(r.filas()).hasSize(1);
        assertThat(r.filas().get(0).idPaciente()).isEqualTo(muchas);
        assertThat(r.filas().get(0).inasistencias()).isEqualTo(3);
        mvc.perform(get("/api/reportes/inasistencia-reiterada").param("desde", esc.dia().toString()).param("hasta", esc.dia().toString())
                        .param("policlinico", "999999").with(authentication(Escenario.autenticacion(b.tens()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idPoliclinico").value(b.idPoliclinico()))
                .andExpect(jsonPath("$.filas.length()").value(1));
    }

    @Test
    void periodoNoValidoSeRechazaYSinDatosSeIndica() {
        var b = esc.base();
        assertThatThrownBy(() -> new FiltroReporte(esc.dia(), esc.dia().minusDays(1), null, null)).isInstanceOf(ErrorNegocio.class);
        esc.como(b.admin());
        assertThat(reportes.ocupacion(new FiltroReporte(esc.dia(), esc.dia(), b.idPoliclinico(), null)).sinDatos()).isTrue();
    }
}
