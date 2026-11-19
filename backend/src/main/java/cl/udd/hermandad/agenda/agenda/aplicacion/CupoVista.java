package cl.udd.hermandad.agenda.agenda.aplicacion;

import cl.udd.hermandad.agenda.agenda.dominio.EstadoCupo;

import java.time.LocalDate;
import java.time.LocalTime;

/** Cupo con su estado derivado, para las vistas de agenda y la búsqueda. */
public record CupoVista(Long id, LocalDate fecha, LocalTime horaInicio, LocalTime horaTermino, int duracion,
                        EstadoCupo estado, boolean habilitadoWeb, String motivoBloqueo,
                        Integer idProfesional, String profesional, Integer idProfesion, String profesion,
                        Integer idBox, String box, Integer idPoliclinico, String policlinico, String direccion,
                        String paciente) {
}
