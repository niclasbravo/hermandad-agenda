package cl.udd.hermandad.agenda.pacientes.aplicacion;

import cl.udd.hermandad.agenda.pacientes.dominio.ResultadoVerificacion;

/** Se publica al registrar una verificación; el módulo de citas pasa las PENDIENTE_VERIFICACION a RESERVADA. */
public record VerificacionRegistrada(Integer idPaciente, Integer idVerificacion, ResultadoVerificacion resultado) {
}
