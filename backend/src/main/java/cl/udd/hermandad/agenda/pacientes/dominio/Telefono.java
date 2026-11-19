package cl.udd.hermandad.agenda.pacientes.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;

/** Teléfonos chilenos: se guardan como «+56912345678» y se muestran como «+56 9 1234 5678». */
public final class Telefono {

    private Telefono() {
    }

    public static String normalizar(String campo, String telefono) {
        if (telefono == null || telefono.isBlank()) {
            throw ErrorNegocio.validacion(campo, "Escribe el número de celular.");
        }
        String digitos = telefono.replaceAll("[^\\d+]", "");
        if (digitos.startsWith("+")) {
            digitos = "+" + digitos.substring(1).replace("+", "");
        }
        if (digitos.matches("9\\d{8}")) {
            digitos = "+56" + digitos;
        } else if (digitos.matches("569\\d{8}")) {
            digitos = "+" + digitos;
        } else if (digitos.matches("\\d{8}")) {
            digitos = "+569" + digitos;
        }
        if (!digitos.matches("\\+\\d{8,15}")) {
            throw ErrorNegocio.validacion(campo, "Escribe un celular válido, por ejemplo +56 9 1234 5678.");
        }
        return digitos;
    }

    public static String formatear(String telefono) {
        if (telefono != null && telefono.matches("\\+569\\d{8}")) {
            return "+56 9 " + telefono.substring(4, 8) + " " + telefono.substring(8);
        }
        return telefono;
    }
}
