package cl.udd.hermandad.agenda.reportes.dominio;

import java.util.List;

/** RF-28. % de inasistencia = inasistencias / (asistidas + inasistencias). */
public class ReporteAsistencia extends Reporte {

    public record Conteo(String etiqueta, long reservadas, long asistidas, long inasistencias) {
        public double porcentajeInasistencia() { return porcentaje(inasistencias, asistidas + inasistencias); }
    }

    private final Conteo total;
    private final Conteo anterior;
    private final List<Conteo> porProfesion;
    private final List<Conteo> evolucion;

    public ReporteAsistencia(FiltroReporte filtro, Conteo total, Conteo anterior, List<Conteo> porProfesion, List<Conteo> evolucion) {
        super(filtro);
        this.total = total;
        this.anterior = anterior;
        this.porProfesion = porProfesion;
        this.evolucion = evolucion;
    }

    public Conteo total() { return total; }
    public Conteo anterior() { return anterior; }
    public List<Conteo> porProfesion() { return porProfesion; }
    public List<Conteo> evolucion() { return evolucion; }

    @Override
    public boolean sinDatos() { return total.reservadas() == 0; }
}
