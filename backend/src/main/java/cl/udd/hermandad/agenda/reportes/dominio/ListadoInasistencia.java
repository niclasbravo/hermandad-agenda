package cl.udd.hermandad.agenda.reportes.dominio;

import java.time.LocalDate;
import java.util.List;

/** RF-24, RN-10. Pacientes con inasistencias en el período iguales o mayores que el umbral. */
public class ListadoInasistencia extends Reporte {

    public record Fila(Integer idPaciente, String paciente, String documento, long inasistencias, LocalDate ultimaFecha,
                       String ultimaProfesion, String telefono) {
    }

    private final int umbral;
    private final List<Fila> filas;

    public ListadoInasistencia(FiltroReporte filtro, int umbral, List<Fila> filas) {
        super(filtro);
        this.umbral = umbral;
        this.filas = filas.stream().filter(f -> f.inasistencias() >= umbral).toList();
    }

    public int umbral() { return umbral; }
    public List<Fila> filas() { return filas; }

    @Override
    public boolean sinDatos() { return filas.isEmpty(); }
}
