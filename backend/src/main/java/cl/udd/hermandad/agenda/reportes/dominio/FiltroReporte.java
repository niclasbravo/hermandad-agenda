package cl.udd.hermandad.agenda.reportes.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Período (mes o rango), policlínico y profesional. */
public record FiltroReporte(LocalDate desde, LocalDate hasta, Integer idPoliclinico, Integer idProfesional) {

    /** CU-07 E1: período no válido. */
    public FiltroReporte {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw ErrorNegocio.validacion("hasta", "El período no es válido: la fecha final debe ser igual o posterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > 366 * 2) {
            throw ErrorNegocio.validacion("hasta", "El período no puede ser mayor a dos años.");
        }
    }

    /** Período anterior de igual largo (para comparar con el mes anterior). */
    public FiltroReporte anterior() {
        boolean mesCompleto = desde.getDayOfMonth() == 1 && hasta.equals(desde.withDayOfMonth(desde.lengthOfMonth()));
        if (mesCompleto) {
            LocalDate d = desde.minusMonths(1);
            return new FiltroReporte(d, d.withDayOfMonth(d.lengthOfMonth()), idPoliclinico, idProfesional);
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        return new FiltroReporte(desde.minusDays(dias), desde.minusDays(1), idPoliclinico, idProfesional);
    }
}
