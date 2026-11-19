package cl.udd.hermandad.agenda.agenda.dominio;

/**
 * Estado derivado del cupo (no se guarda). Prioridad: BLOQUEADO > RESERVADO > NO_PUBLICADO > DISPONIBLE.
 * PASADO se agrega para los cupos libres cuya hora ya pasó (no aparece en la especificación; ver DECISIONES).
 */
public enum EstadoCupo { BLOQUEADO, RESERVADO, NO_PUBLICADO, DISPONIBLE, PASADO }
