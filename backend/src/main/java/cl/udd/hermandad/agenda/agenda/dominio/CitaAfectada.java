package cl.udd.hermandad.agenda.agenda.dominio;

import java.time.LocalDate;
import java.time.LocalTime;

/** Reserva vigente que quedaría sin cupo al modificar o desactivar un bloque (D-10). */
public record CitaAfectada(Long idCita, Long idCupo, String paciente, String documento, String telefono,
                           LocalDate fecha, LocalTime hora) {
}
