package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.agenda.aplicacion.AgendaService;
import cl.udd.hermandad.agenda.catalogo.aplicacion.CatalogoService;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService.AccionAfectada;
import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.CanalRegistro;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Casos encontrados en la revisión de noviembre de 2026: concurrencia, errores claros y mensajes correctos. */
class RobustezTest extends PruebaIntegracion {

    @Autowired AgendamientoService agendamiento;
    @Autowired AgendaService agenda;
    @Autowired CatalogoService catalogo;
    @Autowired PacienteService pacientes;
    @Autowired NotificacionService notificaciones;
    @Autowired MockMvc mvc;

    @Test
    void dosReagendamientosSimultaneosDeLaMismaCitaDejanUnaSolaCitaVigente() throws Exception {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long original = agendamiento.reservar(p, cupos.get(0)).getId();
        SecurityContextHolder.clearContext();

        CyclicBarrier barrera = new CyclicBarrier(2);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Callable<Object>> tareas = new ArrayList<>();
        for (Long destino : List.of(cupos.get(1), cupos.get(2))) {
            tareas.add(() -> {
                esc.como(b.tens());
                barrera.await();
                try {
                    return agendamiento.reagendar(original, destino).getId();
                } catch (Exception e) {
                    return e;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
        }
        List<Future<Object>> r = hilos.invokeAll(tareas, 60, TimeUnit.SECONDS);
        hilos.shutdown();
        Object a = r.get(0).get();
        Object c = r.get(1).get();
        assertThat((a instanceof Long ? 1 : 0) + (c instanceof Long ? 1 : 0)).as("resultados: %s / %s", a, c).isEqualTo(1);
        assertThat(a instanceof Long ? c : a).isInstanceOfSatisfying(ErrorNegocio.class,
                e -> assertThat(e.getCodigo()).isEqualTo("CITA_NO_VIGENTE"));
        assertThat(contar("select count(*) from cita where id_paciente = ? and estado = 'RESERVADA'", p)).isEqualTo(1);
    }

    @Test
    void peticionesMalFormadasRespondenConUnErrorClaroYNoCon500() throws Exception {
        var b = esc.base();
        var tens = authentication(Escenario.autenticacion(b.tens()));
        mvc.perform(delete("/api/citas/1").with(csrf()).with(tens)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/citas").with(csrf()).with(tens).contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/lista-espera").with(tens).accept(MediaType.APPLICATION_XML)).andExpect(status().isNotAcceptable());
        mvc.perform(multipart("/api/publico/reservas/1/certificados").with(csrf()).param("tipo", "FONASA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profesionalConUnPoliclinicoNuloSeRechazaComoValidacion() {
        var b = esc.base();
        esc.como(b.admin());
        assertThatThrownBy(() -> catalogo.registrarProfesional(new CatalogoService.DatosProfesional(b.idProfesion(), "Ana", "Paz",
                false, null, null, null, Arrays.asList((Integer) null))))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.VALIDACION));
    }

    @Test
    void elRutInvalidoDelAdultoSeMarcaEnElCampoDelAdulto() {
        var b = esc.base();
        esc.como(b.tens());
        var d = new DatosPaciente(TipoDocumento.RUT, esc.rut(), "Tomás", "Menor Prueba", LocalDate.now().minusYears(8),
                esc.telefono(), null, "Calle Uno 123", "Masculino", "Chilena",
                new DatosPaciente.DatosAdulto(TipoDocumento.RUT, "12.345.678-9", "Carla Herrera", "+56964445566", "madre"));
        assertThatThrownBy(() -> pacientes.registrar(d, CanalRegistro.TENS))
                .isInstanceOfSatisfying(ErrorNegocio.class, e ->
                        assertThat(((Map<?, ?>) e.getDetalles().get("campos")).keySet().stream().map(String::valueOf).toList())
                                .containsExactly("adulto.numeroDocumento"));
    }

    @Test
    void losMensajesCanceladosQuedanMarcadosComoCanceladosYNoComoFallidos() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long forzada = agendamiento.reservar(p, cupos.get(0)).getId();
        notificaciones.forzarRecordatorio(forzada);
        assertThat(jdbc.queryForList("select m.id_externo from mensaje m join plantilla_mensaje pl on pl.id_plantilla = m.id_plantilla "
                + "where m.id_cita = ? and pl.tipo = 'RECORDATORIO' and m.estado_entrega = 'FALLIDO'", String.class, forzada))
                .isNotEmpty().containsOnly("CANCELADO");

        long anulada = agendamiento.reservar(esc.paciente(b, true), cupos.get(1)).getId();
        agendamiento.anular(anulada, OrigenAnulacion.TENS, null);
        assertThat(jdbc.queryForList("select id_externo from mensaje where id_cita = ? and estado_entrega = 'FALLIDO'", String.class, anulada))
                .isNotEmpty().containsOnly("CANCELADO");
    }

    @Test
    void reservarUnCupoBloqueadoDiceQueEstaBloqueadoYNoQueOtraPersonaLoTomo() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        jdbc.update("insert into bloqueo (id_usuario, motivo) values (?, 'Reunión clínica')", b.admin().idUsuario());
        jdbc.update("update cupo set id_bloqueo = (select max(id_bloqueo) from bloqueo) where id_cupo = ?", cupos.get(0));
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        assertThatThrownBy(() -> agendamiento.reservar(p, cupos.get(0)))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("CUPO_NO_DISPONIBLE");
                    assertThat(e.getMessage()).contains("bloqueado");
                });
    }

    @Test
    void desactivarDosVecesElMismoBloqueAvisaQueYaEstaDesactivado() {
        var b = esc.base();
        esc.bloque(b, "09:00", "10:00");
        Integer idBloque = jdbc.queryForObject("select id_bloque from bloque_horario where id_profesional = ?", Integer.class, b.idProfesional());
        esc.como(b.admin());
        agenda.desactivarBloque(idBloque);
        assertThatThrownBy(() -> agenda.desactivarBloque(idBloque))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("BLOQUE_INACTIVO"));
    }

    @Test
    void resolverAfectadasRechazaCitasDeOtroBloque() throws Exception {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long cita = agendamiento.reservar(p, cupos.get(0)).getId();
        esc.como(b.admin());
        assertThatThrownBy(() -> agendamiento.resolverAfectadas(-1, List.of(new AccionAfectada(cita, AccionAfectada.Tipo.ANULAR, null))))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.VALIDACION));
        assertThat(texto("select estado from cita where id_cita = ?", cita)).isEqualTo("RESERVADA");
    }
}
