package cl.udd.hermandad.agenda.agenda.dominio;

import java.time.LocalDate;

/** Filtros de búsqueda de cupos disponibles (RF-14, RF-34). */
public record FiltroCupos(Integer idPoliclinico, Integer idProfesion, Integer idProfesional,
                          LocalDate desde, LocalDate hasta, boolean soloWeb) {
}
