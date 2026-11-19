package cl.udd.hermandad.agenda.reportes.dominio;

import java.util.List;

/** RF-29, D-13. Demanda = citas no anuladas en el período + inscripciones EN_ESPERA de su profesión en su policlínico. */
public class ReporteDemanda extends Reporte {

    public record Fila(Integer idProfesional, String profesional, String profesion, String policlinicos, long reservas,
                       long enEspera) {
        public long demanda() { return reservas + enEspera; }
    }

    private final List<Fila> filas;

    public ReporteDemanda(FiltroReporte filtro, List<Fila> filas) {
        super(filtro);
        this.filas = filas;
    }

    public List<Fila> filas() { return filas; }
    public long reservas() { return filas.stream().mapToLong(Fila::reservas).sum(); }
    public long enEspera() { return filas.stream().mapToLong(Fila::enEspera).sum(); }

    @Override
    public boolean sinDatos() { return filas.stream().allMatch(f -> f.demanda() == 0); }
}
