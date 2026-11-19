package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.RedactorMensajes;
import cl.udd.hermandad.agenda.confirmaciones.dominio.Mensaje;
import cl.udd.hermandad.agenda.confirmaciones.dominio.Respuesta;
import cl.udd.hermandad.agenda.confirmaciones.dominio.ResultadoLlamada;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.CanalRegistro;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M6 · CU-05: confirmación, recordatorio, refuerzos, respuestas, llamadas y webhook. */
class ConfirmacionesTest extends PruebaIntegracion {

    @Autowired AgendamientoService agendamiento;
    @Autowired NotificacionService notificaciones;
    @Autowired ParametroService parametros;
    @Autowired ListaEsperaService lista;
    @Autowired PacienteService pacientes;
    @Autowired MockMvc mvc;

    private long reservar(Escenario.Base b, String telefono, int indiceCupo) {
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p = esc.paciente(b, true, telefono);
        esc.como(b.tens());
        long id = agendamiento.reservar(p, cupos.get(indiceCupo)).getId();
        esc.sinSesion();
        return id;
    }

    private String estadoMensaje(long cita, String plantilla) {
        return texto("select m.estado_entrega from mensaje m join plantilla_mensaje p on p.id_plantilla = m.id_plantilla "
                + "where m.id_cita = ? and p.nombre_proveedor = ? and m.destinatario = 'PACIENTE' order by m.id_mensaje desc limit 1", cita, plantilla);
    }

    private long idMensaje(long cita, String plantilla) {
        return contar("select max(m.id_mensaje) from mensaje m join plantilla_mensaje p on p.id_plantilla = m.id_plantilla "
                + "where m.id_cita = ? and p.nombre_proveedor = ?", cita, plantilla);
    }

    private String estadoConfirmacion(long cita) {
        return texto("select estado_confirmacion from cita where id_cita = ?", cita);
    }

    @Test
    void caRf19_1_laConfirmacionSaleSinIntervencion() {
        var b = esc.base();
        long cita = reservar(b, esc.telefono(), 0);
        assertThat(estadoMensaje(cita, "hermandad_confirmacion")).isEqualTo("PROGRAMADO");
        assertThat(estadoConfirmacion(cita)).isEqualTo("SIN_ENVIAR");
        notificaciones.enviarPendientes();
        assertThat(estadoMensaje(cita, "hermandad_confirmacion")).isEqualTo("ENVIADO");
        assertThat(estadoConfirmacion(cita)).isEqualTo("ENVIADA");
        assertThat(texto("select id_externo from mensaje where id_mensaje = ?", idMensaje(cita, "hermandad_confirmacion"))).startsWith("sim.");
    }

    @Test
    void caRf19_2_siFallaTrasLosReintentosQuedaNoEnviadaYPorLlamar() {
        var b = esc.base();
        long cita = reservar(b, "+56912340000", 0);
        notificaciones.enviarPendientes();
        notificaciones.enviarPendientes();
        assertThat(estadoMensaje(cita, "hermandad_confirmacion")).isEqualTo("PROGRAMADO");
        notificaciones.enviarPendientes();
        assertThat(estadoMensaje(cita, "hermandad_confirmacion")).isEqualTo("FALLIDO");
        assertThat(estadoConfirmacion(cita)).isEqualTo("NO_ENVIADA");
        assertThat(agendamiento.cita(cita).requiereLlamada(reloj.hoy())).isTrue();
    }

    @Test
    void caRf20_1_elMensajeLlevaPoliclinicoDireccionProfesionalFechaYHora() {
        var b = esc.base();
        long cita = reservar(b, esc.telefono(), 1);
        Mensaje m = notificaciones.detalle(cita).mensajes().get(0);
        String texto = RedactorMensajes.texto(m.getPlantilla(), m.getCita(), Paciente.Destinatario.PACIENTE);
        assertThat(texto).contains("Hola, Rosa.", "Policlínico " + b.policlinico(), "Calle Falsa", "Paula Rojas", "9:20",
                cl.udd.hermandad.agenda.compartido.Textos.capitalizar(cl.udd.hermandad.agenda.compartido.Textos.fechaLarga(esc.dia())));
        // RNF-06: sin información clínica; solo los datos permitidos.
        assertThat(texto).doesNotContainIgnoringCase("diagnóstico").doesNotContain("RUT");
    }

    @Test
    void caRf21_1_elRecordatorioSaleElDiaAntesConBotonesYRf38LosRefuerzosSegunParametros() {
        var b = esc.base();
        esc.como(b.admin());
        parametros.actualizar(Map.of(ParametroService.REFUERZOS_CANTIDAD, "1", ParametroService.REFUERZOS_HORAS_ANTES, "4"));
        try {
            long cita = reservar(b, esc.telefono(), 0);
            assertThat(jdbc.queryForObject("select m.fecha_programada from mensaje m join plantilla_mensaje p on p.id_plantilla = m.id_plantilla "
                    + "where m.id_cita = ? and p.nombre_proveedor = 'hermandad_recordatorio'", java.time.LocalDateTime.class, cita))
                    .isEqualTo(esc.dia().minusDays(1).atTime(10, 0));
            assertThat(jdbc.queryForObject("select m.fecha_programada from mensaje m join plantilla_mensaje p on p.id_plantilla = m.id_plantilla "
                    + "where m.id_cita = ? and p.nombre_proveedor = 'hermandad_refuerzo'", java.time.LocalDateTime.class, cita))
                    .isEqualTo(esc.dia().atTime(5, 0));
            notificaciones.enviarPendientes();
            reloj.fijarEn(esc.dia().minusDays(1).atTime(9, 59));
            notificaciones.enviarPendientes();
            assertThat(estadoMensaje(cita, "hermandad_recordatorio")).isEqualTo("PROGRAMADO");
            reloj.fijarEn(esc.dia().minusDays(1).atTime(10, 0));
            notificaciones.enviarPendientes();
            assertThat(estadoMensaje(cita, "hermandad_recordatorio")).isEqualTo("ENVIADO");
            assertThat(estadoConfirmacion(cita)).isEqualTo("SIN_RESPUESTA");
            assertThat(notificaciones.detalle(cita).mensajes().stream().filter(m -> m.getPlantilla().getNombreProveedor().equals("hermandad_recordatorio"))
                    .allMatch(m -> m.getPlantilla().llevaBotones())).isTrue();
        } finally {
            esc.como(b.admin());
            parametros.actualizar(Map.of(ParametroService.REFUERZOS_CANTIDAD, "", ParametroService.REFUERZOS_HORAS_ANTES, ""));
        }
    }

    @Test
    void reservaTardiaNoProgramaRecordatorio() {
        var b = esc.base();
        reloj.fijarEn(esc.dia().minusDays(1).atTime(11, 0));
        long cita = reservar(b, esc.telefono(), 0);
        assertThat(idMensaje(cita, "hermandad_recordatorio")).isZero();
    }

    @Test
    void respuestaAsistireConfirma() {
        var b = esc.base();
        long cita = reservar(b, esc.telefono(), 0);
        reloj.fijarEn(esc.dia().minusDays(1).atTime(10, 0));
        notificaciones.enviarPendientes();
        notificaciones.procesarRespuesta(idMensaje(cita, "hermandad_recordatorio"), Respuesta.ASISTIRA);
        assertThat(estadoConfirmacion(cita)).isEqualTo("CONFIRMADA");
        assertThat(contar("select count(*) from respuesta_mensaje r join mensaje m on m.id_mensaje = r.id_mensaje where m.id_cita = ?", cita)).isEqualTo(1);
    }

    @Test
    void caRf33_1_noPodreAsistirAnulaLiberaElCupoAvisaYCancelaLosProgramados() {
        var b = esc.base();
        int esperando = esc.paciente(b, true);
        esc.como(b.tens());
        lista.inscribir(esperando, b.idPoliclinico(), b.idProfesion());
        esc.como(b.admin());
        parametros.actualizar(Map.of(ParametroService.REFUERZOS_CANTIDAD, "1", ParametroService.REFUERZOS_HORAS_ANTES, "4"));
        try {
            long cita = reservar(b, esc.telefono(), 0);
            reloj.fijarEn(esc.dia().minusDays(1).atTime(10, 0));
            notificaciones.enviarPendientes();
            esc.sinSesion();
            notificaciones.procesarRespuesta(idMensaje(cita, "hermandad_recordatorio"), Respuesta.NO_ASISTIRA);
            assertThat(jdbc.queryForMap("select estado, origen_anulacion, id_cupo_activo from cita where id_cita = ?", cita))
                    .containsEntry("estado", "ANULADA").containsEntry("origen_anulacion", "PACIENTE_MENSAJE").containsEntry("id_cupo_activo", null);
            assertThat(estadoMensaje(cita, "hermandad_anulacion")).isEqualTo("PROGRAMADO");
            assertThat(estadoMensaje(cita, "hermandad_refuerzo")).isEqualTo("FALLIDO");
            assertThat(contar("select count(*) from auditoria where entidad = 'mensaje' and detalle = 'cancelado: cita anulada' and id_registro = ?",
                    idMensaje(cita, "hermandad_refuerzo"))).isEqualTo(1);
            assertThat(contar("select count(*) from aviso_cupo_liberado where id_cita_anulada = ?", cita)).isEqualTo(1);
            notificaciones.enviarPendientes();
            assertThat(estadoMensaje(cita, "hermandad_anulacion")).isEqualTo("ENVIADO");
        } finally {
            esc.como(b.admin());
            parametros.actualizar(Map.of(ParametroService.REFUERZOS_CANTIDAD, "", ParametroService.REFUERZOS_HORAS_ANTES, ""));
        }
    }

    @Test
    void d07_losMensajesVanAlPacienteYAlAdultoResponsable() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        esc.como(b.tens());
        var menor = new DatosPaciente(TipoDocumento.RUT, esc.rut(), "Martín", "Herrera Lagos", LocalDate.now().minusYears(12),
                "+56963334455", null, "Nataniel Cox 987", "Masculino", "Chilena",
                new DatosPaciente.DatosAdulto(TipoDocumento.RUT, esc.rut(), "Carla Herrera", "+56964445566", "madre"));
        int p = pacientes.registrar(menor, CanalRegistro.TENS).getId();
        pacientes.registrarVerificacion(p, reloj.hoy(), "CESFAM Norte", true, true, null, null);
        long cita = agendamiento.reservar(p, cupos.get(0)).getId();
        assertThat(jdbc.queryForList("select concat(destinatario, ' ', telefono_destino) from mensaje m join plantilla_mensaje p "
                        + "on p.id_plantilla = m.id_plantilla where m.id_cita = ? and p.tipo = 'CONFIRMACION' order by m.id_mensaje", String.class, cita))
                .containsExactly("PACIENTE +56963334455", "ADULTO_RESPONSABLE +56964445566");
        Mensaje alAdulto = notificaciones.detalle(cita).mensajes().stream()
                .filter(m -> m.getDestinatario() == Paciente.Destinatario.ADULTO_RESPONSABLE).findFirst().orElseThrow();
        assertThat(RedactorMensajes.texto(alAdulto.getPlantilla(), alAdulto.getCita(), alAdulto.getDestinatario()))
                .startsWith("Hola. La hora de Martín quedó reservada.");
    }

    @Test
    void rf38_reenvioManualQuedaComoEnviadoPorTens() {
        var b = esc.base();
        long cita = reservar(b, esc.telefono(), 0);
        esc.como(b.tens());
        notificaciones.reenviarRecordatorio(cita);
        assertThat(jdbc.queryForMap("select enviado_por, id_usuario from mensaje where id_cita = ? and enviado_por = 'TENS'", cita))
                .containsEntry("id_usuario", (long) b.tens().idUsuario());
    }

    @Test
    void rf39_resultadoDeLaLlamada() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int p1 = esc.paciente(b, true), p2 = esc.paciente(b, true), p3 = esc.paciente(b, true);
        esc.como(b.tens());
        long c1 = agendamiento.reservar(p1, cupos.get(0)).getId();
        long c2 = agendamiento.reservar(p2, cupos.get(1)).getId();
        long c3 = agendamiento.reservar(p3, cupos.get(2)).getId();
        notificaciones.registrarLlamada(c1, ResultadoLlamada.CONFIRMO, "Viene acompañada de su hija");
        notificaciones.registrarLlamada(c2, ResultadoLlamada.NO_ASISTIRA, null);
        notificaciones.registrarLlamada(c3, ResultadoLlamada.NO_CONTESTO, null);
        assertThat(estadoConfirmacion(c1)).isEqualTo("CONFIRMADA_TELEFONO");
        assertThat(jdbc.queryForMap("select estado, origen_anulacion from cita where id_cita = ?", c2))
                .containsEntry("estado", "ANULADA").containsEntry("origen_anulacion", "TENS");
        assertThat(estadoConfirmacion(c3)).isEqualTo("SIN_ENVIAR");
        assertThat(texto("select estado from cita where id_cita = ?", c3)).isEqualTo("RESERVADA");
        assertThat(texto("select comentario from llamada_confirmacion where id_cita = ?", c1)).isEqualTo("Viene acompañada de su hija");
        assertThat(contar("select count(*) from llamada_confirmacion where id_cita in (?,?,?)", c1, c2, c3)).isEqualTo(3);
    }

    // ---------------- Webhook (sección 10.5) ----------------

    private static String firma(String cuerpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secreto-de-prueba".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void webhookVerificacionDeMeta() throws Exception {
        mvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "verificacion-de-prueba")
                .param("hub.challenge", "12345")).andExpect(status().isOk()).andExpect(content().string("12345"));
        mvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "otro")
                .param("hub.challenge", "12345")).andExpect(status().isForbidden());
    }

    @Test
    void webhookConFirmaInvalidaSeRechaza() throws Exception {
        String cuerpo = "{\"entry\":[]}";
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("X-Hub-Signature-256", "sha256=00")).andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isUnauthorized());
    }

    @Test
    void webhookActualizaEstadosYRespuestasEIgnoraLosRepetidos() throws Exception {
        var b = esc.base();
        long cita = reservar(b, esc.telefono(), 0);
        reloj.fijarEn(esc.dia().minusDays(1).atTime(10, 0));
        notificaciones.enviarPendientes();
        long idRec = idMensaje(cita, "hermandad_recordatorio");
        String externo = texto("select id_externo from mensaje where id_mensaje = ?", idRec);
        String estados = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"" + externo + "\",\"status\":\"read\"},"
                + "{\"id\":\"" + externo + "\",\"status\":\"delivered\"}]}}]}]}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(estados).header("X-Hub-Signature-256", firma(estados)))
                    .andExpect(status().isOk());
        }
        assertThat(texto("select estado_entrega from mensaje where id_mensaje = ?", idRec)).isEqualTo("LEIDO");
        String respuesta = "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"id\":\"wamid.entrante." + cita + "\",\"type\":\"button\","
                + "\"context\":{\"id\":\"" + externo + "\"},\"button\":{\"payload\":\"ASISTIRA\",\"text\":\"Asistiré\"}}]}}]}]}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(respuesta).header("X-Hub-Signature-256", firma(respuesta)))
                    .andExpect(status().isOk());
        }
        assertThat(contar("select count(*) from respuesta_mensaje where id_mensaje = ?", idRec)).isEqualTo(1);
        assertThat(estadoConfirmacion(cita)).isEqualTo("CONFIRMADA");
    }
}
