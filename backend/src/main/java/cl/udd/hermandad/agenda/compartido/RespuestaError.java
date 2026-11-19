package cl.udd.hermandad.agenda.compartido;

import java.util.Map;

public record RespuestaError(String codigo, String mensaje, Map<String, Object> detalles) {
}
