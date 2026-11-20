package cl.udd.hermandad.agenda.servicios;

import cl.udd.hermandad.agenda.confirmaciones.aplicacion.RedactorMensajes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las plantillas aprobadas en Meta tienen variables {{1}}..{{n}}: WhatsApp rechaza el envío si se mandan más o menos
 * parámetros. Cada plantilla debe enviar solo sus variables, en el orden en que aparecen en el texto.
 */
class RedactorMensajesTest {

    @Test
    void cadaPlantillaEnviaSoloSusVariablesEnOrdenDeAparicion() {
        Map<String, List<String>> porNombre = RedactorMensajes.plantillasIniciales().stream()
                .collect(Collectors.toMap(p -> p[1], p -> RedactorMensajes.variablesEnOrden(p[2])));

        List<String> confirmacion = List.of("nombre", "Fecha", "hora", "profesion", "profesional", "policlinico", "box", "direccion");
        assertThat(porNombre.get("hermandad_confirmacion")).isEqualTo(confirmacion);
        assertThat(porNombre.get("hermandad_confirmacion_pendiente")).isEqualTo(confirmacion);
        assertThat(porNombre.get("hermandad_recordatorio")).containsExactly("Fecha", "hora", "profesion", "policlinico", "box");
        assertThat(porNombre.get("hermandad_refuerzo")).containsExactly("fecha", "Fecha", "hora", "profesion", "policlinico", "box");
        assertThat(porNombre.get("hermandad_anulacion")).containsExactly("fecha", "hora");
        assertThat(porNombre.get("hermandad_anulacion_policlinico")).containsExactly("nombre", "fecha", "hora");
    }

    @Test
    void unaVariableRepetidaSeEnviaUnaSolaVez() {
        assertThat(RedactorMensajes.variablesEnOrden("{{hora}} y {{nombre}}, otra vez {{hora}}"))
                .containsExactly("hora", "nombre");
    }
}
