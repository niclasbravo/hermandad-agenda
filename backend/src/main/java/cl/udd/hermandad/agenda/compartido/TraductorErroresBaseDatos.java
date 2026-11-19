package cl.udd.hermandad.agenda.compartido;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Traduce los errores de la base (disparadores con SQLSTATE 45000 y claves únicas)
 * a errores de negocio con mensajes simples.
 */
public final class TraductorErroresBaseDatos {

    public static final String MSJ_CUPO_TOMADO = "Otra persona tomó este horario hace un momento. Elige otro.";
    public static final String MSJ_NO_HABILITADO = "Este paciente aún no tiene registrada la verificación de FONASA y CESFAM. Regístrala en su ficha antes de reservar.";
    public static final String MSJ_MENOR_SIN_ADULTO = "Para registrar a un menor de edad se necesitan los datos de un adulto responsable.";
    public static final String MSJ_BLOQUE_SUPERPUESTO = "Este horario se cruza con otro bloque del mismo profesional o del mismo box. Elige otro horario o box.";

    private TraductorErroresBaseDatos() {
    }

    public static Optional<ErrorNegocio> traducir(Throwable error) {
        Throwable actual = error;
        int profundidad = 0;
        while (actual != null && profundidad < 15) {
            if (actual instanceof SQLException sql) {
                Optional<ErrorNegocio> traducido = traducirSql(sql);
                if (traducido.isPresent()) {
                    return traducido;
                }
            }
            String texto = actual.getMessage();
            if (texto != null && texto.contains("uq_cita_cupo_activo")) {
                return Optional.of(ErrorNegocio.conflicto("CUPO_TOMADO", MSJ_CUPO_TOMADO));
            }
            actual = actual.getCause();
            profundidad++;
        }
        return Optional.empty();
    }

    private static Optional<ErrorNegocio> traducirSql(SQLException sql) {
        String texto = sql.getMessage() == null ? "" : sql.getMessage();
        if ("45000".equals(sql.getSQLState())) {
            if (texto.contains("superpone")) {
                return Optional.of(ErrorNegocio.conflicto("BLOQUE_SUPERPUESTO", MSJ_BLOQUE_SUPERPUESTO));
            }
            if (texto.contains("menor de edad")) {
                return Optional.of(ErrorNegocio.regla("MENOR_SIN_ADULTO", MSJ_MENOR_SIN_ADULTO));
            }
            if (texto.contains("bloqueado")) {
                return Optional.of(ErrorNegocio.conflicto("CUPO_TOMADO", "Este horario ya no está disponible. Elige otro."));
            }
            if (texto.contains("verificaci")) {
                return Optional.of(ErrorNegocio.regla("PACIENTE_NO_HABILITADO", MSJ_NO_HABILITADO));
            }
            return Optional.of(ErrorNegocio.regla("REGLA_BASE_DATOS", "No se pudo guardar porque no cumple una regla del sistema."));
        }
        if (texto.contains("uq_cita_cupo_activo")) {
            return Optional.of(ErrorNegocio.conflicto("CUPO_TOMADO", MSJ_CUPO_TOMADO));
        }
        if (texto.contains("uq_paciente_documento")) {
            return Optional.of(ErrorNegocio.conflicto("DOCUMENTO_DUPLICADO", "Ya existe un paciente con este documento."));
        }
        if (texto.contains("Duplicate entry")) {
            return Optional.of(ErrorNegocio.conflicto("DUPLICADO", "Ya existe un registro con estos datos."));
        }
        return Optional.empty();
    }
}
