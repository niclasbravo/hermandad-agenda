package cl.udd.hermandad.agenda.confirmaciones.aplicacion;

import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;

/**
 * El paciente respondió «No podré asistir» o avisó por teléfono que no asistirá.
 * El módulo de citas la recibe y llama a AgendamientoService.anular (evita la dependencia circular entre servicios).
 */
public record SolicitudAnulacion(Long idCita, OrigenAnulacion origen, String motivo, Integer idUsuario) {
}
