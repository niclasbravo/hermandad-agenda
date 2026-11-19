package cl.udd.hermandad.agenda.listaespera.dominio;

/** D-05, RF-43. */
public enum MotivoRetiro {
    HORA_ASIGNADA("Se le asignó una hora"),
    DESISTE("Ya no necesita la atención"),
    OTRO("Otro motivo");

    private final String texto;

    MotivoRetiro(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }
}
