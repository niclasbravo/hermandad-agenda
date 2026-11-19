package cl.udd.hermandad.agenda.pacientes.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * Documento de identidad (objeto de valor). El RUT se guarda normalizado como «12345678-5»
 * y el dígito verificador se valida solo si el tipo es RUT (D-14).
 */
@Embeddable
public class DocumentoIdentidad {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumento tipo;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numero;

    protected DocumentoIdentidad() {
    }

    private DocumentoIdentidad(TipoDocumento tipo, String numero) {
        this.tipo = tipo;
        this.numero = numero;
    }

    /** Crea el documento normalizado y validado. */
    public static DocumentoIdentidad de(TipoDocumento tipo, String numero) {
        TipoDocumento t = tipo == null ? TipoDocumento.RUT : tipo;
        DocumentoIdentidad d = new DocumentoIdentidad(t, normalizar(t, numero));
        d.validar();
        return d;
    }

    /** Quita puntos, espacios y guiones; deja «cuerpo-DV» para el RUT. */
    public static String normalizar(TipoDocumento tipo, String numero) {
        if (numero == null) {
            return "";
        }
        String limpio = numero.trim().toUpperCase().replaceAll("[\\s.]", "");
        if (tipo == TipoDocumento.RUT) {
            limpio = limpio.replace("-", "").replaceFirst("^0+", "");
            if (limpio.length() < 2) {
                return limpio;
            }
            return limpio.substring(0, limpio.length() - 1) + "-" + limpio.charAt(limpio.length() - 1);
        }
        return limpio;
    }

    public void validar() {
        if (numero == null || numero.isBlank()) {
            throw ErrorNegocio.validacion("numeroDocumento", "Escribe el número de documento.");
        }
        if (numero.length() > 20) {
            throw ErrorNegocio.validacion("numeroDocumento", "El número de documento es demasiado largo.");
        }
        if (tipo == TipoDocumento.RUT && !rutValido(numero)) {
            throw ErrorNegocio.validacion("numeroDocumento", "El RUT no es válido. Revisa el número y el dígito verificador.");
        }
    }

    /** Módulo 11. Acepta «12345678-5» normalizado. */
    public static boolean rutValido(String normalizado) {
        if (normalizado == null || !normalizado.matches("\\d{1,9}-[\\dK]")) {
            return false;
        }
        String cuerpo = normalizado.substring(0, normalizado.indexOf('-'));
        char dv = normalizado.charAt(normalizado.length() - 1);
        return calcularDv(cuerpo) == dv;
    }

    public static char calcularDv(String cuerpo) {
        int suma = 0;
        int factor = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        if (resto == 11) {
            return '0';
        }
        if (resto == 10) {
            return 'K';
        }
        return Character.forDigit(resto, 10);
    }

    /** «12.345.678-5» para el RUT; el número tal cual para los demás. */
    public String formatear() {
        if (tipo != TipoDocumento.RUT || !numero.contains("-")) {
            return numero;
        }
        String cuerpo = numero.substring(0, numero.indexOf('-'));
        return conPuntos(cuerpo) + numero.substring(numero.indexOf('-'));
    }

    /** «12.345.***-5»: para la reserva en línea (no revela el documento completo). */
    public String enmascarar() {
        if (tipo != TipoDocumento.RUT || !numero.contains("-")) {
            int visibles = Math.min(3, numero.length());
            return "*".repeat(Math.max(0, numero.length() - visibles)) + numero.substring(numero.length() - visibles);
        }
        String cuerpo = numero.substring(0, numero.indexOf('-'));
        String formateado = conPuntos(cuerpo);
        String oculto = formateado.length() > 3 ? formateado.substring(0, formateado.length() - 3) + "***" : "***";
        return oculto + numero.substring(numero.indexOf('-'));
    }

    private static String conPuntos(String cuerpo) {
        StringBuilder sb = new StringBuilder();
        int n = cuerpo.length();
        for (int i = 0; i < n; i++) {
            sb.append(cuerpo.charAt(i));
            int resto = n - i - 1;
            if (resto > 0 && resto % 3 == 0) {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    public TipoDocumento getTipo() { return tipo; }
    public String getNumero() { return numero; }

    @Override
    public boolean equals(Object o) {
        return o instanceof DocumentoIdentidad d && d.tipo == tipo && Objects.equals(d.numero, numero);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipo, numero);
    }
}
