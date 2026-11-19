package cl.udd.hermandad.agenda.reportes.dominio;

/** Base de los reportes: todos se calculan con consultas sobre los registros vigentes (RNF-12). */
public abstract class Reporte {

    private final FiltroReporte filtro;

    protected Reporte(FiltroReporte filtro) {
        this.filtro = filtro;
    }

    public FiltroReporte getFiltro() {
        return filtro;
    }

    /** CU-07 E2. */
    public abstract boolean sinDatos();

    public static double porcentaje(long parte, long total) {
        return total == 0 ? 0.0 : Math.round(parte * 1000.0 / total) / 10.0;
    }
}
