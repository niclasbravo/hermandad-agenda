package cl.udd.hermandad.agenda.agenda.aplicacion;

import java.time.LocalDate;
import java.time.LocalTime;

public record DatosBloque(Integer idProfesional, Integer idBox, Integer diaSemana, LocalTime horaInicio,
                          LocalTime horaTermino, LocalDate vigenciaDesde, LocalDate vigenciaHasta) {
}
