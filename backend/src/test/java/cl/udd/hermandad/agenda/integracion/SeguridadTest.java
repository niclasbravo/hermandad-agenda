package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RNF-04 (perfiles), RNF-05 (auditoría) y D-17 (nada se borra). */
class SeguridadTest extends PruebaIntegracion {

    @Autowired MockMvc mvc;
    @Autowired AgendamientoService agendamiento;
    @Autowired PasswordEncoder encoder;

    private static final String BLOQUE = "{\"idProfesional\":%d,\"idBox\":%d,\"diaSemana\":6,\"horaInicio\":\"09:00\",\"horaTermino\":\"10:00\","
            + "\"vigenciaDesde\":\"%s\",\"vigenciaHasta\":\"%s\"}";

    @Test
    void caRnf04_1_laTensQueIntentaCrearUnBloqueRecibe403() throws Exception {
        var b = esc.base();
        String cuerpo = BLOQUE.formatted(b.idProfesional(), b.idBox(), esc.dia(), esc.dia().plusWeeks(2));
        mvc.perform(post("/api/bloques").with(csrf()).with(authentication(Escenario.autenticacion(b.tens())))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(post("/api/bloques").with(csrf()).with(authentication(Escenario.autenticacion(b.admin())))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
    }

    @Test
    void caRnf04_2_sinSesionNoSeMuestraNingunaInformacion() throws Exception {
        for (String ruta : List.of("/api/agenda-dia", "/api/pacientes?documento=12345678-5", "/api/policlinicos", "/api/auth/yo",
                "/api/reportes/inasistencia-reiterada?desde=2026-01-01&hasta=2026-01-31")) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SIN_SESION"));
        }
        mvc.perform(get("/api/publico/policlinicos")).andExpect(status().isOk());
    }

    @Test
    void permisosPorPerfilSegunLaTablaDeLaSeccion6() throws Exception {
        var b = esc.base();
        mvc.perform(post("/api/citas").with(csrf()).with(authentication(Escenario.autenticacion(b.admin())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"idPaciente\":1,\"idCupo\":1}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/ocupacion?desde=2026-01-01&hasta=2026-01-31").with(authentication(Escenario.autenticacion(b.tens()))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parametros").with(csrf()).with(authentication(Escenario.autenticacion(b.tens())))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/bloqueos").with(csrf()).with(authentication(Escenario.autenticacion(b.tens())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"idsCupo\":[1],\"motivo\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/inasistencia-reiterada?desde=2026-01-01&hasta=2026-01-31")
                .with(authentication(Escenario.autenticacion(b.tens())))).andExpect(status().isOk());
        // Sin token CSRF no se aceptan escrituras.
        mvc.perform(post("/api/bloques").with(authentication(Escenario.autenticacion(b.admin())))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void ingresoValidaContrasenaYQueElPerfilCoincidaConElBoton() throws Exception {
        int n = esc.n();
        int perfil = jdbc.queryForObject("select id_perfil from perfil where nombre = 'TENS/TONS'", Integer.class);
        int pol = esc.base().idPoliclinico();
        jdbc.update("insert into usuario (id_perfil, id_policlinico, nombre_completo, nombre_usuario, hash_contrasena) values (?,?,?,?,?)",
                perfil, pol, "Carolina Prueba", "carolina" + n, encoder.encode("Clave-segura-1"));
        String ok = "{\"usuario\":\"carolina" + n + "\",\"contrasena\":\"Clave-segura-1\",\"perfil\":\"TENS\"}";
        mvc.perform(post("/api/auth/ingresar").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idPoliclinicoBase").value(pol));
        mvc.perform(post("/api/auth/ingresar").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(ok.replace("\"TENS\"", "\"ADMINISTRACION\"")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value("Este usuario no tiene perfil de Administración."));
        mvc.perform(post("/api/auth/ingresar").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(ok.replace("Clave-segura-1", "otra"))).andExpect(status().isUnauthorized());
    }

    @Test
    void caRnf05_1_alAnularQuedaRegistroConUsuarioFechaYAccion() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true);
        esc.como(b.tens());
        long cita = agendamiento.reservar(p, cupos.get(0)).getId();
        agendamiento.anular(cita, OrigenAnulacion.TENS, "Avisó por teléfono");
        var fila = jdbc.queryForMap("select id_usuario, fecha_hora, accion, entidad from auditoria where entidad = 'cita' and id_registro = ? and accion = 'ANULAR'", cita);
        assertThat(fila).containsEntry("id_usuario", (long) b.tens().idUsuario()).containsEntry("accion", "ANULAR");
        assertThat(fila.get("fecha_hora")).isNotNull();
    }

    @Test
    void d17_noHayEndpointsParaBorrar() throws Exception {
        var b = esc.base();
        for (String ruta : List.of("/api/citas/1", "/api/profesionales/1", "/api/bloques/1", "/api/lista-espera/1")) {
            int estado = mvc.perform(delete(ruta).with(csrf()).with(authentication(Escenario.autenticacion(b.admin()))))
                    .andReturn().getResponse().getStatus();
            assertThat(estado).as(ruta).isGreaterThanOrEqualTo(400);
        }
    }
}
