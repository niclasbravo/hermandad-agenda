package cl.udd.hermandad.agenda.servicios;

import cl.udd.hermandad.agenda.confirmaciones.dominio.IServicioMensajeria;
import cl.udd.hermandad.agenda.confirmaciones.infraestructura.MensajeriaConNumerosPermitidos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Demo con el número de prueba de Meta: solo los números permitidos reciben WhatsApp real; el resto va al simulador. */
class MensajeriaConNumerosPermitidosTest {

    static class Registro implements IServicioMensajeria {
        final String nombre;
        final List<String> enviados = new ArrayList<>();
        Registro(String nombre) { this.nombre = nombre; }
        @Override
        public String enviarPlantilla(String telefono, String plantilla, List<String> parametros, List<String> botones) {
            enviados.add(telefono);
            return nombre + "-" + enviados.size();
        }
    }

    @Test
    void envia_por_whatsapp_real_solo_a_los_numeros_permitidos() {
        Registro real = new Registro("wamid");
        Registro simulador = new Registro("sim");
        var m = new MensajeriaConNumerosPermitidos(real, simulador,
                MensajeriaConNumerosPermitidos.desdeTexto("+56 9 5018 9480, 56911112222"));

        assertThat(m.enviarPlantilla("+56950189480", "hermandad_confirmacion", List.of(), List.of())).startsWith("wamid");
        assertThat(m.enviarPlantilla("+56 9 1111-2222", "hermandad_recordatorio", List.of(), List.of())).startsWith("wamid");
        assertThat(m.enviarPlantilla("+56912345678", "hermandad_recordatorio", List.of(), List.of())).startsWith("sim");

        assertThat(real.enviados).containsExactly("+56950189480", "+56 9 1111-2222");
        assertThat(simulador.enviados).containsExactly("+56912345678");
    }

    @Test
    void sin_lista_todos_salen_por_whatsapp() {
        var m = new MensajeriaConNumerosPermitidos(new Registro("wamid"), new Registro("sim"), Set.of());
        assertThat(m.esPermitido("+56912345678")).isTrue();
        assertThat(MensajeriaConNumerosPermitidos.desdeTexto("  ")).isEmpty();
    }
}
