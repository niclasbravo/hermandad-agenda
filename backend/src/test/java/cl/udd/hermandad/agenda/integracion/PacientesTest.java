package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.CanalRegistro;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import cl.udd.hermandad.agenda.pacientes.dominio.ResultadoVerificacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M3 · CU-03. */
class PacientesTest extends PruebaIntegracion {

    @Autowired PacienteService pacientes;
    @Autowired AgendamientoService agendamiento;
    @Autowired NotificacionService notificaciones;
    @Autowired MockMvc mvc;

    @Test
    void caRf09_1_buscarPorRutConYSinPuntosMuestraLosDatos() throws Exception {
        var b = esc.base();
        esc.como(b.tens());
        String rut = esc.rut();
        int id = pacientes.registrar(esc.datos(rut, LocalDate.of(1952, 3, 1), esc.telefono()), CanalRegistro.TENS).getId();
        String cuerpo = rut.substring(0, rut.indexOf('-'));
        String conPuntos = cuerpo.substring(0, 2) + "." + cuerpo.substring(2, 5) + "." + cuerpo.substring(5) + rut.substring(rut.indexOf('-'));
        for (String consulta : List.of(rut, conPuntos, rut.replace("-", ""))) {
            mvc.perform(get("/api/pacientes").param("documento", consulta).with(authentication(Escenario.autenticacion(b.tens()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.documentoFormateado").value(conPuntos))
                    .andExpect(jsonPath("$.nombreCompleto").value("Rosa María Díaz Prueba"));
        }
    }

    @Test
    void caRf09_2_siNoExisteLoIndicaYOfreceRegistrarlo() throws Exception {
        var b = esc.base();
        mvc.perform(get("/api/pacientes").param("documento", esc.rut()).with(authentication(Escenario.autenticacion(b.tens()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detalles.ofrecerRegistro").value(true));
    }

    @Test
    void caRf10_1_seGuardaSinCorreoYSeEncuentraPorRut() {
        var b = esc.base();
        esc.como(b.tens());
        String rut = esc.rut();
        pacientes.registrar(esc.datos(rut, LocalDate.of(1980, 1, 1), esc.telefono()), CanalRegistro.TENS);
        assertThat(pacientes.buscar(rut)).isPresent();
        assertThat(contar("select count(*) from paciente where numero_documento = ? and correo is null and fecha_aviso_mensajes is not null", rut)).isEqualTo(1);
        assertThat(contar("select count(*) from auditoria a join paciente p on p.id_paciente = a.id_registro "
                + "where a.entidad = 'paciente' and a.accion = 'CREAR' and p.numero_documento = ?", rut)).isEqualTo(1);
    }

    @Test
    void caRf10_2_documentoDuplicadoNoSeCreaYMuestraElExistente() {
        var b = esc.base();
        esc.como(b.tens());
        String rut = esc.rut();
        int id = pacientes.registrar(esc.datos(rut, LocalDate.of(1980, 1, 1), esc.telefono()), CanalRegistro.TENS).getId();
        assertThatThrownBy(() -> pacientes.registrar(esc.datos(rut, LocalDate.of(1981, 1, 1), esc.telefono()), CanalRegistro.TENS))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("DOCUMENTO_DUPLICADO");
                    assertThat(e.getDetalles().get("idPaciente")).isEqualTo(id);
                });
        assertThat(contar("select count(*) from paciente where numero_documento = ?", rut)).isEqualTo(1);
    }

    @Test
    void d14_pasaporteNoValidaDigitoVerificadorYRutInvalidoSeRechaza() {
        var b = esc.base();
        esc.como(b.tens());
        var pasaporte = new DatosPaciente(TipoDocumento.PASAPORTE, "AB" + esc.n(), "Jean", "Pierre", LocalDate.of(1990, 1, 1),
                esc.telefono(), null, "Calle 1", "Masculino", "Haitiana", null);
        assertThat(pacientes.registrar(pasaporte, CanalRegistro.TENS).getId()).isPositive();
        assertThatThrownBy(() -> pacientes.registrar(esc.datos("12345678-9", LocalDate.of(1990, 1, 1), esc.telefono()), CanalRegistro.TENS))
                .isInstanceOf(ErrorNegocio.class).hasMessageContaining("RUT no es válido");
    }

    @Test
    void caRf11_1_lasConfirmacionesSiguientesVanAlNumeroNuevo() {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int id = esc.paciente(b, true, "+56911112222");
        esc.como(b.tens());
        var d = pacientes.obtener(id);
        pacientes.actualizar(id, new DatosPaciente(TipoDocumento.RUT, d.getDocumento().getNumero(), d.getNombres(), d.getApellidos(),
                d.getFechaNacimiento(), "+56 9 3333 4444", null, d.getDireccion(), d.getGenero(), d.getNacionalidad(), null));
        long cita = agendamiento.reservar(id, cupos.get(0)).getId();
        assertThat(texto("select telefono_destino from mensaje where id_cita = ? and destinatario = 'PACIENTE' limit 1", cita))
                .isEqualTo("+56933334444");
        assertThat(contar("select count(*) from auditoria where entidad = 'paciente' and accion = 'MODIFICAR' and id_registro = ?", id)).isEqualTo(1);
    }

    @Test
    void caRf12_1_menorSinAdultoSeExigeAntesDeGuardar() {
        var b = esc.base();
        esc.como(b.tens());
        var menor = esc.datos(esc.rut(), LocalDate.now().minusYears(9), esc.telefono());
        assertThatThrownBy(() -> pacientes.registrar(menor, CanalRegistro.TENS))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("MENOR_SIN_ADULTO"));
        String rutAdulto = esc.rut();
        var conAdulto = new DatosPaciente(TipoDocumento.RUT, esc.rut(), "Tomás", "Fuentes Vega", LocalDate.now().minusYears(9),
                esc.telefono(), null, "Gay 369", "Masculino", "Chilena",
                new DatosPaciente.DatosAdulto(TipoDocumento.RUT, rutAdulto, "Valentina Vega Rojas", "+56966667788", "madre"));
        int id = pacientes.registrar(conAdulto, CanalRegistro.TENS).getId();
        // Si el documento del adulto ya existe, se reutiliza.
        var hermano = new DatosPaciente(TipoDocumento.RUT, esc.rut(), "Ana", "Fuentes Vega", LocalDate.now().minusYears(7),
                esc.telefono(), null, "Gay 369", "Femenino", "Chilena",
                new DatosPaciente.DatosAdulto(TipoDocumento.RUT, rutAdulto, "Valentina Vega Rojas", "+56966667788", "madre"));
        int id2 = pacientes.registrar(hermano, CanalRegistro.TENS).getId();
        assertThat(contar("select count(*) from adulto_responsable where numero_documento = ?", rutAdulto)).isEqualTo(1);
        assertThat(contar("select count(distinct id_adulto_responsable) from paciente where id_paciente in (?,?)", id, id2)).isEqualTo(1);
    }

    @Test
    void caRf13_1_verificacionHabilitaSoloConAmbasCasillasYGuardaCesfamYFecha() {
        var b = esc.base();
        int id = esc.paciente(b, false);
        esc.como(b.tens());
        var v1 = pacientes.registrarVerificacion(id, reloj.hoy(), "CESFAM Norte", true, false, "Falta CESFAM", null);
        assertThat(v1.getResultado()).isEqualTo(ResultadoVerificacion.NO_CUMPLE);
        assertThat(pacientes.obtener(id).estaHabilitado()).isFalse();
        var v2 = pacientes.registrarVerificacion(id, reloj.hoy(), "CESFAM Norte", true, true, null, null);
        assertThat(v2.getResultado()).isEqualTo(ResultadoVerificacion.HABILITADO);
        assertThat(pacientes.obtener(id).estaHabilitado()).isTrue();
        assertThat(contar("select count(*) from verificacion_requisitos where id_paciente = ? and cesfam_inscripcion = 'CESFAM Norte' "
                + "and fecha_verificacion = ?", id, reloj.hoy())).isEqualTo(2);
        assertThat(contar("select count(*) from auditoria where accion = 'VERIFICAR' and entidad = 'verificacion_requisitos' and id_registro = ?",
                v2.getId())).isEqualTo(1);
    }

    @Test
    void caRf23_1_historialListaLasCitasConFechaProfesionalYEstado() throws Exception {
        var b = esc.base();
        List<Long> cupos = esc.bloque(b, "09:00", "10:00");
        int id = esc.paciente(b, true);
        esc.como(b.tens());
        agendamiento.reservar(id, cupos.get(0));
        long anulada = agendamiento.reservar(id, cupos.get(1)).getId();
        agendamiento.anular(anulada, cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion.TENS, null);
        mvc.perform(get("/api/pacientes/" + id + "/historial").with(authentication(Escenario.autenticacion(b.tens()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estadoTexto").value("Anulada"))
                .andExpect(jsonPath("$[1].estadoTexto").value("Próxima"))
                .andExpect(jsonPath("$[1].profesional").value("Paula Rojas " + "" + b.profesion().replace("Medicina ", "")))
                .andExpect(jsonPath("$[1].fecha").value(esc.dia().toString()));
    }
}
