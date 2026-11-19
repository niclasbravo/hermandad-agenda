package cl.udd.hermandad.agenda.reportes.dominio;

import java.util.List;

/** RF-27. Ofrecidos = publicados y no bloqueados. */
public class ReporteOcupacion extends Reporte {

    public record Fila(Integer idPoliclinico, String policlinico, Integer idProfesional, String profesional, String profesion,
                       long ofrecidos, long reservados) {
        public long disponibles() { return ofrecidos - reservados; }
        public double porcentajeOcupacion() { return porcentaje(reservados, ofrecidos); }
    }

    private final List<Fila> filas;
    private final long ofrecidosAnterior;
    private final long reservadosAnterior;

    public ReporteOcupacion(FiltroReporte filtro, List<Fila> filas, long ofrecidosAnterior, long reservadosAnterior) {
        super(filtro);
        this.filas = filas;
        this.ofrecidosAnterior = ofrecidosAnterior;
        this.reservadosAnterior = reservadosAnterior;
    }

    public long ofrecidos() { return filas.stream().mapToLong(Fila::ofrecidos).sum(); }
    public long reservados() { return filas.stream().mapToLong(Fila::reservados).sum(); }
    public long disponibles() { return ofrecidos() - reservados(); }
    public double porcentajeOcupacion() { return porcentaje(reservados(), ofrecidos()); }
    public double porcentajeOcupacionAnterior() { return porcentaje(reservadosAnterior, ofrecidosAnterior); }
    public long ofrecidosAnterior() { return ofrecidosAnterior; }
    public long reservadosAnterior() { return reservadosAnterior; }
    public List<Fila> filas() { return filas; }

    @Override
    public boolean sinDatos() { return ofrecidos() == 0; }
}
