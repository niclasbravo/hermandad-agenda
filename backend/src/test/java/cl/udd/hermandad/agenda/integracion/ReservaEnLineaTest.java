package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.agenda.dominio.FiltroCupos;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.aplicacion.ReservaEnLineaService;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoAdjunto;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU-08: reserva en línea (RF-34, RF-35, RF-36, RF-41, RN-13, RN-14). */
class ReservaEnLineaTest extends PruebaIntegracion {

    @Autowired ReservaEnLineaService enLinea;
    @Autowired AgendamientoService agendamiento;
    @Autowired PacienteService pacientes;
    @Autowired ParametroService parametros;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final LocalDate NACIMIENTO = LocalDate.of(1970, 5, 10);
    private static final byte[] PDF = "%PDF-1.4\n%prueba\n".getBytes(StandardCharsets.ISO_8859_1);

    private List<Long> cuposWeb(Escenario.Base b) {
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        jdbc.update("update cupo set habilitado_web = true where id_cupo in (?,?)", cupos.get(0), cupos.get(1));
        return cupos;
    }

    private String rutDe(int idPaciente) {
        return texto("select numero_documento from paciente where id_paciente = ?", idPaciente);
    }

    private ObjectNode identificar(String numero, LocalDate fecha, String ip) throws Exception {
        String r = mvc.perform(post("/api/publico/identificacion").with(csrf()).with(x -> { x.setRemoteAddr(ip); return x; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipoDocumento", "RUT", "numero", numero, "fechaNacimiento", fecha.toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return (ObjectNode) json.readTree(r);
    }

    @Test
    void caRf35_1_documentoInexistenteYFechaDistintaDanLaMismaRespuesta() throws Exception {
        var b = esc.base();
        int p = esc.paciente(b, true);
        ObjectNode fechaDistinta = identificar(rutDe(p), NACIMIENTO.plusDays(1), "10.1.1.1");
        ObjectNode inexistente = identificar(esc.rut(), NACIMIENTO, "10.1.1.2");
        ObjectNode correcta = identificar(rutDe(p), NACIMIENTO, "10.1.1.3");
        fechaDistinta.remove("token");
        inexistente.remove("token");
        assertThat(fechaDistinta.get("estado").asText()).isEqualTo("NUEVO");
        assertThat(fechaDistinta.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(() -> inexistente.fieldNames());
        assertThat(fechaDistinta.get("nombrePila").isNull()).isTrue();
        assertThat(inexistente.get("nombrePila").isNull()).isTrue();
        assertThat(correcta.get("estado").asText()).isEqualTo("IDENTIFICADO");
        assertThat(fechaDistinta.get("documentoEnmascarado").asText()).matches("\\d{2}\\.\\d{3}\\.\\*\\*\\*-[\\dK]");
        // La respuesta pública no trae datos de la ficha (teléfono, dirección, apellidos).
        assertThat(correcta.toString()).doesNotContain("Calle Uno", "Díaz Prueba", "+569");
    }

    @Test
    void rutInvalidoDa400YLosIntentosFallidosTerminanEn429() throws Exception {
        String malo = "{\"tipoDocumento\":\"RUT\",\"numero\":\"12.345.678-9\",\"fechaNacimiento\":\"1970-01-01\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/publico/identificacion").with(csrf()).with(x -> { x.setRemoteAddr("10.9.9.9"); return x; })
                    .contentType(MediaType.APPLICATION_JSON).content(malo)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/publico/identificacion").with(csrf()).with(x -> { x.setRemoteAddr("10.9.9.9"); return x; })
                .contentType(MediaType.APPLICATION_JSON).content(malo)).andExpect(status().isTooManyRequests());
    }

    @Test
    void caRf34_1_reservaEnLineaDesapareceDeLaWebYDeLaAgendaYSeEnviaLaConfirmacion() {
        var b = esc.base();
        List<Long> cupos = cuposWeb(b);
        int p = esc.paciente(b, true);
        var ident = enLinea.identificar(TipoDocumento.RUT, rutDe(p), NACIMIENTO, "10.2.0.1");
        long cita = enLinea.reservar(ident.token(), cupos.get(0)).getId();
        assertThat(jdbc.queryForMap("select estado, canal_reserva, id_usuario_reserva from cita where id_cita = ?", cita))
                .containsEntry("estado", "RESERVADA").containsEntry("canal_reserva", "EN_LINEA").containsEntry("id_usuario_reserva", null);
        var filtro = new FiltroCupos(b.idPoliclinico(), null, null, esc.dia(), esc.dia(), false);
        assertThat(enLinea.cupos(b.idPoliclinico(), null, null, esc.dia())).extracting(c -> c.id()).containsExactly(cupos.get(1));
        assertThat(agendamiento.consultarCupos(filtro)).extracting(c -> c.id()).doesNotContain(cupos.get(0));
        assertThat(contar("select count(*) from mensaje m join plantilla_mensaje p on p.id_plantilla = m.id_plantilla where m.id_cita = ? "
                + "and p.nombre_proveedor = 'hermandad_confirmacion'", cita)).isEqualTo(1);
        assertThat(contar("select count(*) from auditoria where entidad = 'cita' and id_registro = ? and id_usuario is null", cita)).isEqualTo(1);
    }

    @Test
    void cupoNoWebSeRechaza() {
        var b = esc.base();
        List<Long> cupos = cuposWeb(b);
        int p = esc.paciente(b, true);
        var ident = enLinea.identificar(TipoDocumento.RUT, rutDe(p), NACIMIENTO, "10.3.0.1");
        assertThatThrownBy(() -> enLinea.reservar(ident.token(), cupos.get(2)))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("CUPO_NO_WEB"));
    }

    @Test
    void rn14_topeDeReservasEnLineaDa422() {
        var b = esc.base();
        List<Long> cupos = cuposWeb(b);
        int p = esc.paciente(b, true);
        esc.como(b.admin());
        parametros.actualizar(Map.of(ParametroService.TOPE_RESERVAS_WEB, "1"));
        esc.sinSesion();
        try {
            var ident = enLinea.identificar(TipoDocumento.RUT, rutDe(p), NACIMIENTO, "10.4.0.1");
            enLinea.reservar(ident.token(), cupos.get(0));
            assertThatThrownBy(() -> enLinea.reservar(ident.token(), cupos.get(1)))
                    .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                        assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.REGLA_NEGOCIO);
                        assertThat(e.getMessage()).startsWith("Ya tienes 1 hora reservada en línea.");
                    });
        } finally {
            esc.como(b.admin());
            parametros.actualizar(Map.of(ParametroService.TOPE_RESERVAS_WEB, ""));
        }
    }

    @Test
    void caRf36_1_y_rf41_pacienteNuevoQuedaPendienteSubeCertificadosYLaTensLoVerifica() {
        var b = esc.base();
        List<Long> cupos = cuposWeb(b);
        String rut = esc.rut();
        var ident = enLinea.identificar(TipoDocumento.RUT, rut, NACIMIENTO, "10.5.0.1");
        assertThat(ident.estado()).isEqualTo(ReservaEnLineaService.EstadoIdentificacion.NUEVO);
        var datos = new DatosPaciente(null, null, "Rosa Elena", "Muñoz Díaz", null, "+56967778899", null, "Maipú 741",
                "Femenino", "Chilena", null);
        enLinea.registrar(ident.token(), datos, "10.5.0.1");
        int idPaciente = (int) contar("select id_paciente from paciente where numero_documento = ?", rut);
        assertThat(jdbc.queryForMap("select canal_registro, id_usuario_registro, fecha_nacimiento from paciente where id_paciente = ?", idPaciente))
                .containsEntry("canal_registro", "EN_LINEA").containsEntry("id_usuario_registro", null);
        assertThat(contar("select count(*) from verificacion_requisitos where id_paciente = ?", idPaciente)).isZero();
        long cita = enLinea.reservar(ident.token(), cupos.get(0)).getId();
        assertThat(texto("select estado from cita where id_cita = ?", cita)).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(contar("select count(*) from mensaje m join plantilla_mensaje p on p.id_plantilla = m.id_plantilla where m.id_cita = ? "
                + "and p.nombre_proveedor = 'hermandad_confirmacion_pendiente'", cita)).isEqualTo(1);
        assertThatThrownBy(() -> enLinea.subirCertificado(ident.token(), cita, TipoAdjunto.CERTIFICADO_FONASA, "x.exe", "MZ binario".getBytes()))
                .isInstanceOf(ErrorNegocio.class).hasMessageContaining("Solo se aceptan");
        var doc = enLinea.subirCertificado(ident.token(), cita, TipoAdjunto.CERTIFICADO_FONASA, "certificado_fonasa.pdf", PDF);
        assertThat(texto("select tipo_mime from documento_adjunto where id_documento = ?", doc.getId())).isEqualTo("application/pdf");
        esc.como(b.tens());
        var v = pacientes.registrarVerificacion(idPaciente, reloj.hoy(), "CESFAM Norte", true, true, null, List.of(doc.getId()));
        assertThat(contar("select id_verificacion from documento_adjunto where id_documento = ?", doc.getId())).isEqualTo(v.getId().longValue());
        assertThat(texto("select estado from cita where id_cita = ?", cita)).isEqualTo("RESERVADA");
    }

    @Test
    void registroEnLineaConDocumentoExistenteDaMensajeGeneralSinCrearDuplicado() {
        var b = esc.base();
        int p = esc.paciente(b, true);
        var ident = enLinea.identificar(TipoDocumento.RUT, rutDe(p), NACIMIENTO.plusDays(3), "10.6.0.1");
        assertThat(ident.estado()).isEqualTo(ReservaEnLineaService.EstadoIdentificacion.NUEVO);
        var datos = new DatosPaciente(null, null, "Otra", "Persona", null, "+56967778811", null, "Maipú 1", "Femenino", "Chilena", null);
        assertThatThrownBy(() -> enLinea.registrar(ident.token(), datos, "10.6.0.1"))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getMessage()).isEqualTo(ReservaEnLineaService.MSJ_NO_PUDIMOS));
        assertThat(contar("select count(*) from paciente where numero_documento = ?", rutDe(p))).isEqualTo(1);
    }
}
