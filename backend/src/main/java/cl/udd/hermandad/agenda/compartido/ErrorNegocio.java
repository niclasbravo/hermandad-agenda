package cl.udd.hermandad.agenda.compartido;

import java.util.Map;

/**
 * Error con un código estable y un mensaje en lenguaje simple (RNF-02).
 * El tipo decide la respuesta HTTP en {@link ManejadorErrores}; el dominio no conoce HTTP.
 */
public class ErrorNegocio extends RuntimeException {

    public enum Tipo { VALIDACION, NO_AUTENTICADO, SIN_PERMISO, NO_ENCONTRADO, CONFLICTO, REGLA_NEGOCIO, DEMASIADOS_INTENTOS }

    private final Tipo tipo;
    private final String codigo;
    private final Map<String, Object> detalles;

    public ErrorNegocio(Tipo tipo, String codigo, String mensaje) {
        this(tipo, codigo, mensaje, Map.of());
    }

    public ErrorNegocio(Tipo tipo, String codigo, String mensaje, Map<String, Object> detalles) {
        super(mensaje);
        this.tipo = tipo;
        this.codigo = codigo;
        this.detalles = detalles == null ? Map.of() : detalles;
    }

    public static ErrorNegocio regla(String codigo, String mensaje) {
        return new ErrorNegocio(Tipo.REGLA_NEGOCIO, codigo, mensaje);
    }

    public static ErrorNegocio conflicto(String codigo, String mensaje) {
        return new ErrorNegocio(Tipo.CONFLICTO, codigo, mensaje);
    }

    public static ErrorNegocio conflicto(String codigo, String mensaje, Map<String, Object> detalles) {
        return new ErrorNegocio(Tipo.CONFLICTO, codigo, mensaje, detalles);
    }

    public static ErrorNegocio noEncontrado(String mensaje) {
        return new ErrorNegocio(Tipo.NO_ENCONTRADO, "NO_ENCONTRADO", mensaje);
    }

    public static ErrorNegocio validacion(String campo, String mensaje) {
        return new ErrorNegocio(Tipo.VALIDACION, "VALIDACION", mensaje, Map.of("campos", Map.of(campo, mensaje)));
    }

    public static ErrorNegocio sinPermiso(String mensaje) {
        return new ErrorNegocio(Tipo.SIN_PERMISO, "SIN_PERMISO", mensaje);
    }

    public Tipo getTipo() { return tipo; }
    public String getCodigo() { return codigo; }
    public Map<String, Object> getDetalles() { return detalles; }
}
