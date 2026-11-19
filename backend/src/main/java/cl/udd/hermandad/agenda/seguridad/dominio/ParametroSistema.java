package cl.udd.hermandad.agenda.seguridad.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** Parámetros clave-valor que define la Administración (D-01, D-02, D-04, D-13). NULL = sin configurar. */
@Entity
@Table(name = "parametro_sistema")
public class ParametroSistema {

    @Id
    @Column(name = "clave", length = 50)
    private String clave;

    @Column(name = "valor", length = 100)
    private String valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", nullable = false)
    private TipoDato tipoDato;

    @Column(name = "descripcion", nullable = false, length = 250)
    private String descripcion;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    protected ParametroSistema() {
    }

    public ParametroSistema(String clave, String valor, TipoDato tipoDato, String descripcion) {
        this.clave = clave;
        this.tipoDato = tipoDato;
        this.descripcion = descripcion;
        this.valor = valor;
        validar();
    }

    public void cambiar(String nuevoValor, Integer idUsuario, LocalDateTime ahora) {
        this.valor = nuevoValor == null || nuevoValor.isBlank() ? null : nuevoValor.trim();
        validar();
        this.idUsuario = idUsuario;
        this.fechaModificacion = ahora;
    }

    /** Valida el valor según tipo_dato. */
    public void validar() {
        if (valor == null) {
            return;
        }
        try {
            switch (tipoDato) {
                case ENTERO -> {
                    int n = Integer.parseInt(valor);
                    if (n < 0) {
                        throw error("Escribe un número igual o mayor que 0.");
                    }
                }
                case PORCENTAJE -> {
                    int n = Integer.parseInt(valor);
                    if (n < 0 || n > 100) {
                        throw error("Escribe un porcentaje entre 0 y 100.");
                    }
                }
                case HORAS -> {
                    for (String parte : valor.split(",")) {
                        if (Integer.parseInt(parte.trim()) <= 0) {
                            throw error("Escribe horas mayores que 0, separadas por coma.");
                        }
                    }
                }
                case BOOLEANO -> {
                    if (!valor.equalsIgnoreCase("true") && !valor.equalsIgnoreCase("false")) {
                        throw error("El valor debe ser sí o no.");
                    }
                }
                case TEXTO -> {
                    if ("RECORDATORIO_HORA".equals(clave)) {
                        LocalTime.parse(valor.length() == 4 ? "0" + valor : valor);
                    }
                    if ("REFUERZOS_HORAS_ANTES".equals(clave)) {
                        for (String parte : valor.split(",")) {
                            if (Integer.parseInt(parte.trim()) <= 0) {
                                throw error("Escribe horas mayores que 0, separadas por coma. Por ejemplo: 48,4");
                            }
                        }
                    }
                }
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            throw error("El valor no es válido.");
        }
    }

    private ErrorNegocio error(String mensaje) {
        return ErrorNegocio.validacion(clave, mensaje);
    }

    public String getClave() { return clave; }
    public String getValor() { return valor; }
    public TipoDato getTipoDato() { return tipoDato; }
    public String getDescripcion() { return descripcion; }
    public Integer getIdUsuario() { return idUsuario; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
}
