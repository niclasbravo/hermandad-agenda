package cl.udd.hermandad.agenda.reportes.dominio;

import java.util.List;

/** RF-29, D-13. Demanda = citas no anuladas en el período + inscripciones EN_ESPERA de su profesión en su policlínico. */
public class ReporteDemanda extends Reporte {

    public record Fila(Integer idProfesional, String profesional, String profesion, String policlinicos, long reservas,
                       long enEspera) {
        public long demanda() { return reservas + enEspera; }
    }

    private final List<Fila> filas;
    private final long enEsperaTotal;

    /**
     * La lista de espera es por policlínico y profesión: cada fila muestra la de su profesión (compartida entre los
     * profesionales de esa profesión), pero el total la cuenta una sola vez.
     */
    public ReporteDemanda(FiltroReporte filtro, List<Fila> filas, long enEsperaTotal) {
        super(filtro);
        this.filas = filas;
        this.enEsperaTotal = enEsperaTotal;
    }

    public List<Fila> filas() { return filas; }
    public long reservas() { return filas.stream().mapToLong(Fila::reservas).sum(); }
    public long enEspera() { return enEsperaTotal; }

    public long demanda() { return reservas() + enEsperaTotal; }

    @Override
    /** La lista de espera es la de hoy: un período sin reservas no tiene datos aunque haya personas esperando. */
    public boolean sinDatos() { return reservas() == 0; }
}
