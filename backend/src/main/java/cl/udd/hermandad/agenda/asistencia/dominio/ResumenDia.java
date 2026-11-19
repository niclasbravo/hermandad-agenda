package cl.udd.hermandad.agenda.asistencia.dominio;

/** Tarjetas de resumen de M-06. */
public record ResumenDia(long citasHoy, long confirmadas, long porLlamar, long sinRespuesta, long enListaEspera,
                         long pendientesVerificacion) {
}
