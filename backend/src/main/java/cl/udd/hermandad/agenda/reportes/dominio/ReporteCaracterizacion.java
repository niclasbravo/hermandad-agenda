package cl.udd.hermandad.agenda.reportes.dominio;

import java.util.List;

/** RF-30. Pacientes atendidos (al menos una cita ASISTIDA en el período). Las categorías suman el total. */
public class ReporteCaracterizacion extends Reporte {

    public record Categoria(String nombre, long cantidad) {
    }

    private final long total;
    private final List<Categoria> porEdad;
    private final List<Categoria> porGenero;
    private final List<Categoria> porNacionalidad;
    private final long adultos;
    private final long ninos;
    private final long adultosMayores;

    public ReporteCaracterizacion(FiltroReporte filtro, long total, List<Categoria> porEdad, List<Categoria> porGenero,
                                  List<Categoria> porNacionalidad, long adultos, long ninos, long adultosMayores) {
        super(filtro);
        this.total = total;
        this.porEdad = porEdad;
        this.porGenero = porGenero;
        this.porNacionalidad = porNacionalidad;
        this.adultos = adultos;
        this.ninos = ninos;
        this.adultosMayores = adultosMayores;
    }

    public long total() { return total; }
    public List<Categoria> porEdad() { return porEdad; }
    public List<Categoria> porGenero() { return porGenero; }
    public List<Categoria> porNacionalidad() { return porNacionalidad; }
    public long adultos() { return adultos; }
    public long ninos() { return ninos; }
    public long adultosMayores() { return adultosMayores; }
    public double porcentajeAdultosMayores() { return porcentaje(adultosMayores, total); }

    @Override
    public boolean sinDatos() { return total == 0; }
}
