package cl.udd.hermandad.agenda.seguridad.aplicacion;

/** Evento que se publica al cambiar un parámetro (por ejemplo, el % de cupos web). */
public record ParametroCambiado(String clave, String valorAnterior, String valorNuevo) {
}
