package cl.udd.hermandad.agenda.compartido;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Formatos chilenos para fechas, horas y nombres (RNF-02). */
public final class Textos {

    public static final Locale ES_CL = Locale.forLanguageTag("es-CL");
    private static final DateTimeFormatter CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Textos() {
    }

    /** «martes 13 de octubre». */
    public static String fechaLarga(LocalDate fecha) {
        String dia = fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ES_CL);
        String mes = fecha.getMonth().getDisplayName(TextStyle.FULL, ES_CL);
        return dia + " " + fecha.getDayOfMonth() + " de " + mes;
    }

    /** «13/10/2026». */
    public static String fechaCorta(LocalDate fecha) {
        return fecha.format(CORTA);
    }

    /** «9:30». */
    public static String hora(LocalTime hora) {
        return hora.getHour() + ":" + String.format("%02d", hora.getMinute());
    }

    /** «09:30». */
    public static String hora2(LocalTime hora) {
        return String.format("%02d:%02d", hora.getHour(), hora.getMinute());
    }

    public static String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase(ES_CL) + texto.substring(1);
    }

    public static String primerNombre(String nombres) {
        if (nombres == null) {
            return "";
        }
        String limpio = nombres.trim();
        int espacio = limpio.indexOf(' ');
        return espacio < 0 ? limpio : limpio.substring(0, espacio);
    }

    public static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
