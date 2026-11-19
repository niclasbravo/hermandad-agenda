package cl.udd.hermandad.agenda.seguridad.dominio;

/** Operaciones de la tabla de permisos (sección 6 de la especificación). */
public enum Operacion {
    MANTENER_CATALOGO,
    MANTENER_BLOQUES,
    BLOQUEAR_CUPOS,
    CONFIGURAR_PARAMETROS,
    VER_AGENDA_CUPOS,
    VER_PACIENTES,
    EDITAR_PACIENTES,
    RESERVAR,
    VER_AGENDA_DIA,
    REGISTRAR_ASISTENCIA,
    VER_REPORTES_GESTION,
    VER_INASISTENCIA_REITERADA
}
