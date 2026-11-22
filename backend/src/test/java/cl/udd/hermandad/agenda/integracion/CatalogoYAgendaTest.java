package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.agenda.aplicacion.AgendaService;
import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.agenda.aplicacion.DatosBloque;
import cl.udd.hermandad.agenda.agenda.dominio.EstadoCupo;
import cl.udd.hermandad.agenda.agenda.dominio.FiltroCupos;
import cl.udd.hermandad.agenda.catalogo.aplicacion.CatalogoService;
import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M1 · CU-01 y M2 · CU-02. */
class CatalogoYAgendaTest extends PruebaIntegracion {

    @Autowired CatalogoService catalogo;
    @Autowired AgendaService agenda;
    @Autowired AgendamientoService agendamiento;
    @Autowired ParametroService parametros;
    @Autowired MockMvc mvc;

    @Test
    void caRf01_1_policlinicoConBoxQuedaSeleccionableParaBloques() {
        var b = esc.base();
        esc.como(b.admin());
        Policlinico p = catalogo.crearPoliclinico("Nuevo " + esc.n(), "San Isidro 218", "Santiago Centro", List.of("Box 1", "Box 2"));
        assertThat(p.getBoxes()).hasSize(2);
        jdbc.update("insert into profesional_policlinico (id_profesional, id_policlinico) values (?,?)", b.idProfesional(), p.getId());
        int idBox = p.boxesOrdenados().get(0).getId();
        agenda.crearBloque(new DatosBloque(b.idProfesional(), idBox, 3, LocalTime.of(9, 0), LocalTime.of(10, 0), esc.dia(), esc.dia().plusWeeks(2)));
        assertThat(contar("select count(*) from bloque_horario where id_box = ?", idBox)).isEqualTo(1);
        assertThat(contar("select count(*) from auditoria where entidad = 'policlinico' and id_registro = ?", p.getId())).isEqualTo(1);
    }

    @Test
    void caRf02_1_profesionalHeredaLaDuracionDeSuProfesion() {
        var b = esc.base(30);
        esc.como(b.admin());
        var p = catalogo.registrarProfesional(new CatalogoService.DatosProfesional(b.idProfesion(), "Tomás", "Pinto", false,
                null, null, null, List.of(b.idPoliclinico())));
        assertThat(p.duracionAtencion()).isEqualTo(30);
        List<Long> cupos = esc.bloque(b, p.getId(), b.idBox2(), 1, "09:00", "10:00");
        assertThat(cupos).hasSize(2);
    }

    @Test
    void caRf03_1_laFichaMuestraLaCredencial() throws Exception {
        var b = esc.base();
        esc.como(b.admin());
        var p = catalogo.registrarProfesional(new CatalogoService.DatosProfesional(b.idProfesion(), "Camila", "Soto", false,
                "Cirujana dentista", "Universidad de Chile", "456789", List.of(b.idPoliclinico())));
        mvc.perform(get("/api/profesionales/" + p.getId()).with(authentication(Escenario.autenticacion(b.admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credencial.tituloProfesional").value("Cirujana dentista"))
                .andExpect(jsonPath("$.credencial.casaEstudios").value("Universidad de Chile"))
                .andExpect(jsonPath("$.credencial.registroPrestador").value("456789"));
    }

    @Test
    void caRf05_1_duracionSeGuardaSeUsaEnBloquesNuevosYRechazaCero() {
        var b = esc.base(null);
        esc.como(b.admin());
        assertThatThrownBy(() -> catalogo.definirDuracion(b.idProfesion(), 0, null))
                .isInstanceOf(ErrorNegocio.class).extracting("tipo").isEqualTo(ErrorNegocio.Tipo.VALIDACION);
        assertThatThrownBy(() -> catalogo.definirDuracion(b.idProfesion(), null, null)).isInstanceOf(ErrorNegocio.class);
        catalogo.definirDuracion(b.idProfesion(), 45, null);
        assertThat(contar("select duracion_min from profesion where id_profesion = ?", b.idProfesion())).isEqualTo(45);
        assertThat(esc.bloque(b, "09:00", "10:30")).hasSize(2);
    }

    @Test
    void cu01_e3_profesionSinDuracionAvisaYNoGeneraCupos() {
        var b = esc.base(null);
        esc.como(b.admin());
        var p = catalogo.profesional(b.idProfesional());
        assertThat(CatalogoService.avisoDuracion(p)).contains("No se generarán cupos hasta definir la duración de " + b.profesion() + ".");
        assertThatThrownBy(() -> agenda.crearBloque(new DatosBloque(b.idProfesional(), b.idBox(), 1, LocalTime.of(9, 0),
                LocalTime.of(10, 0), esc.dia(), esc.dia().plusWeeks(1))))
                .isInstanceOf(ErrorNegocio.class).hasMessageContaining("no tiene duración definida");
    }

    @Test
    void caRf04_1_elBloqueApareceEnLaAgendaDeEsaSemana() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        esc.como(b.admin());
        List<CupoVista> semana = agenda.agendaSemanal(b.idProfesional(), b.idPoliclinico(), esc.dia());
        assertThat(semana).extracting(CupoVista::fecha).containsOnly(esc.dia());
        assertThat(semana).hasSize(3).allMatch(c -> c.estado() == EstadoCupo.DISPONIBLE);
    }

    @Test
    void caRf04_2_modificarBloqueConReservasMuestraLasAfectadas() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long idCita = agendamiento.reservar(p, cupos.get(1)).getId();
        esc.como(b.admin());
        Integer idBloque = jdbc.queryForObject("select id_bloque from bloque_horario where id_profesional = ?", Integer.class, b.idProfesional());
        assertThatThrownBy(() -> agenda.modificarBloque(idBloque, new DatosBloque(b.idProfesional(), b.idBox(), 1,
                LocalTime.of(10, 0), LocalTime.of(11, 0), esc.dia().minusDays(7), esc.dia().plusWeeks(4))))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("RESERVAS_AFECTADAS");
                    assertThat((List<?>) e.getDetalles().get("afectadas")).hasSize(1);
                });
        assertThat(texto("select estado from cita where id_cita = ?", idCita)).isEqualTo("RESERVADA");
        assertThat(contar("select activo from bloque_horario where id_bloque = ?", idBloque)).isEqualTo(1);
    }

    @Test
    void modificarBloqueSinReservasDejaLosCuposSobrantesComoHistorialBloqueado() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        esc.como(b.admin());
        Integer idBloque = jdbc.queryForObject("select id_bloque from bloque_horario where id_profesional = ?", Integer.class, b.idProfesional());
        agenda.modificarBloque(idBloque, new DatosBloque(b.idProfesional(), b.idBox(), 1, LocalTime.of(10, 0), LocalTime.of(11, 0),
                esc.dia().minusDays(7), esc.dia().plusWeeks(4)));
        assertThat(contar("select count(*) from cupo where id_cupo in (?,?,?) and id_bloqueo is not null", cupos.toArray())).isEqualTo(3);
        assertThat(esc.cuposDe(b.idProfesional(), esc.dia())).hasSize(6);
        assertThat(agendamiento.consultarCupos(new FiltroCupos(b.idPoliclinico(), null, b.idProfesional(), esc.dia(), esc.dia(), false)))
                .extracting(c -> c.horaInicio().toString()).containsExactly("10:00", "10:20", "10:40");
    }

    @Test
    void caRf06_1_medicoDeNueveADiezGeneraTresCupos() {
        var b = esc.base(20);
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        assertThat(jdbc.queryForList("select date_format(hora_inicio, '%H:%i') from cupo where id_cupo in (?,?,?) order by 1",
                String.class, cupos.toArray())).containsExactly("09:00", "09:20", "09:40");
    }

    @Test
    void caRf06_2_psicologoDeNueveADiezYMediaGeneraDosCupos() {
        var b = esc.base(45);
        List<Long> cupos = esc.bloque(b, "09:00", "10:30");
        assertThat(jdbc.queryForList("select date_format(hora_inicio, '%H:%i') from cupo where id_cupo in (?,?) order by 1",
                String.class, cupos.toArray())).containsExactly("09:00", "09:45");
    }

    @Test
    void d09_bloqueSuperpuestoSeRechazaCon409() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        esc.como(b.admin());
        assertThatThrownBy(() -> agenda.crearBloque(new DatosBloque(b.idProfesional(), b.idBox2(), 1, LocalTime.of(9, 40),
                LocalTime.of(11, 0), esc.dia(), esc.dia().plusWeeks(1))))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("BLOQUE_SUPERPUESTO");
                    assertThat(e.getMessage()).contains("lunes de 09:00 a 10:00");
                });
    }

    /** D-10: un bloque modificado deja el anterior cerrado (vigencia hasta ayer). No debe estorbar ni duplicar cupos. */
    @Test
    void d10_recrearUnBloqueDespuesDeModificarloYDesactivarloNoChocaConElCerrado() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        Integer original = jdbc.queryForObject("select id_bloque from bloque_horario where id_profesional = ?", Integer.class, b.idProfesional());
        reloj.fijarEn(esc.dia().atTime(8, 0)); // el bloque ya empezó: al modificarlo se cierra en vez de desactivarse
        esc.como(b.admin());
        var nuevo = agenda.modificarBloque(original, new DatosBloque(b.idProfesional(), b.idBox(), 1, LocalTime.of(9, 0),
                LocalTime.of(9, 40), esc.dia().minusDays(7), esc.dia().plusWeeks(4)));
        assertThat(contar("select activo from bloque_horario where id_bloque = ?", original)).isEqualTo(1);
        assertThat(agenda.bloquesDelProfesional(b.idProfesional())).extracting(x -> x.getId()).containsExactly(nuevo.getId());

        agenda.desactivarBloque(nuevo.getId());
        var recreado = agenda.crearBloque(new DatosBloque(b.idProfesional(), b.idBox(), 1, LocalTime.of(9, 0), LocalTime.of(10, 0),
                esc.dia().minusDays(7), esc.dia().plusWeeks(4)));
        assertThat(recreado.getVigenciaDesde()).isEqualTo(esc.dia());
        assertThat(agenda.agendaSemanal(b.idProfesional(), b.idPoliclinico(), esc.dia()))
                .hasSize(3).allMatch(c -> c.estado() == EstadoCupo.DISPONIBLE);
    }

    @Test
    void d10_laSerieUsaLosCuposDelBloqueNuevoYNoLosDelCerrado() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        Integer original = jdbc.queryForObject("select id_bloque from bloque_horario where id_profesional = ?", Integer.class, b.idProfesional());
        reloj.fijarEn(esc.dia().atTime(8, 0));
        esc.como(b.admin());
        var nuevo = agenda.modificarBloque(original, new DatosBloque(b.idProfesional(), b.idBox(), 1, LocalTime.of(9, 0),
                LocalTime.of(9, 40), esc.dia().minusDays(7), esc.dia().plusWeeks(4)));
        Long primero = jdbc.queryForObject("select id_cupo from cupo where id_bloque = ? and fecha = ? and hora_inicio = '09:00'",
                Long.class, nuevo.getId(), esc.dia());
        esc.como(b.tens());
        var serie = agendamiento.previsualizarSerie(b.idProfesional(), primero, 3);
        assertThat(serie.sinCupo()).isEmpty();
        assertThat(serie.conCupo()).hasSize(3);
    }

    @Test
    void d10_alVolverAExtenderLaVigenciaLosCuposVuelvenAOfrecerse() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        Integer idBloque = jdbc.queryForObject("select id_bloque from bloque_horario where id_profesional = ?", Integer.class, b.idProfesional());
        esc.como(b.admin());
        LocalDate semanaSiguiente = esc.dia().plusWeeks(1);
        agenda.modificarBloque(idBloque, new DatosBloque(b.idProfesional(), b.idBox(), 1, LocalTime.of(9, 0), LocalTime.of(10, 0),
                esc.dia().minusDays(1), esc.dia()));
        assertThat(contar("select count(*) from cupo where id_bloque = ? and fecha = ? and id_bloqueo is not null", idBloque, semanaSiguiente)).isEqualTo(3);
        agenda.modificarBloque(idBloque, new DatosBloque(b.idProfesional(), b.idBox(), 1, LocalTime.of(9, 0), LocalTime.of(10, 0),
                esc.dia().minusDays(1), esc.dia().plusWeeks(4)));
        assertThat(contar("select count(*) from cupo where id_bloque = ? and fecha = ? and id_bloqueo is not null", idBloque, semanaSiguiente)).isZero();
        assertThat(contar("select count(*) from cupo where id_bloque = ? and fecha = ?", idBloque, semanaSiguiente)).isEqualTo(3);
    }

    @Test
    void desactivarUnBloqueConservaSuHistorialEnLaVistaSemanal() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        Integer idBloque = jdbc.queryForObject("select id_bloque from bloque_horario where id_profesional = ?", Integer.class, b.idProfesional());
        reloj.fijarEn(esc.dia().plusDays(1).atTime(8, 0)); // el lunes de prueba ya pasó
        esc.como(b.admin());
        agenda.desactivarBloque(idBloque);
        assertThat(agenda.agendaSemanal(b.idProfesional(), b.idPoliclinico(), esc.dia())).hasSize(3);
        assertThat(agenda.agendaSemanal(b.idProfesional(), b.idPoliclinico(), esc.dia().plusWeeks(1))).isEmpty();
    }

    @Test
    void caRf07_1_cupoBloqueadoNoApareceYNoSePuedeReservar() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        esc.como(b.admin());
        agenda.bloquearCupos(List.of(cupos.get(0)), null, null, null, null, "Reunión clínica");
        assertThat(contar("select count(*) from auditoria where accion = 'BLOQUEAR' and detalle like ?", "Reunión clínica%")).isPositive();
        assertThat(agendamiento.consultarCupos(new FiltroCupos(b.idPoliclinico(), null, null, esc.dia(), esc.dia(), false)))
                .extracting(CupoVista::id).doesNotContain(cupos.get(0));
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        assertThatThrownBy(() -> agendamiento.reservar(p, cupos.get(0)))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.CONFLICTO));
    }

    @Test
    void caRf08_1_cupoFueraDelPlazoNoApareceYApareceAlEntrarAlPeriodo() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        var filtro = new FiltroCupos(b.idPoliclinico(), null, null, esc.dia().plusWeeks(1), esc.dia().plusWeeks(1), false);
        esc.como(b.tens());
        assertThat(agendamiento.consultarCupos(filtro)).isEmpty();
        reloj.fijarEn(esc.dia().plusDays(6).atTime(8, 0));
        assertThat(agendamiento.consultarCupos(filtro)).hasSize(3);
    }

    @Test
    void caRf37_1_enLaWebSoloSeOfreceElPorcentajeConfigurado() {
        var b = esc.base();
        esc.como(b.admin());
        parametros.actualizar(Map.of(ParametroService.PORCENTAJE_CUPOS_WEB, "50"));
        try {
            List<Long> cupos = esc.bloque(b, "09:00", "11:00");
            assertThat(cupos).hasSize(6);
            var web = agendamiento.consultarCupos(new FiltroCupos(b.idPoliclinico(), null, null, esc.dia(), esc.dia(), true));
            var todos = agendamiento.consultarCupos(new FiltroCupos(b.idPoliclinico(), null, null, esc.dia(), esc.dia(), false));
            assertThat(web).hasSize(3);
            assertThat(todos).hasSize(6);
            assertThat(web).extracting(c -> c.horaInicio().toString()).containsExactly("09:00", "09:40", "10:20");
        } finally {
            esc.como(b.admin());
            parametros.actualizar(Map.of(ParametroService.PORCENTAJE_CUPOS_WEB, ""));
        }
    }

    @Test
    void rf40_agendaPorBox() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        esc.como(b.admin());
        assertThat(agenda.agendaPorBox(b.idBox(), esc.dia())).hasSize(3).allMatch(c -> c.idBox() == b.idBox());
        assertThat(agenda.agendaPorBox(b.idBox2(), esc.dia())).isEmpty();
    }
}
