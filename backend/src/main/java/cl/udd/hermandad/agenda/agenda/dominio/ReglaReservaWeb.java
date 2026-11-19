package cl.udd.hermandad.agenda.agenda.dominio;

import java.util.ArrayList;
import java.util.List;

/** RF-37, D-01, D-02. Porcentaje de cupos web (repartidos de forma pareja) y tope de reservas en línea. */
public class ReglaReservaWeb {

    private final Integer porcentaje;
    private final Integer tope;

    public ReglaReservaWeb(Integer porcentaje, Integer tope) {
        this.porcentaje = porcentaje;
        this.tope = tope;
    }

    /** ceil(n × % / 100). NULL = 0 %. */
    public int cuposWeb(int n) {
        if (porcentaje == null || porcentaje <= 0 || n <= 0) {
            return 0;
        }
        return (int) Math.min(n, Math.ceil(n * porcentaje / 100.0));
    }

    /** Índices (0..n-1) de los cupos web, repartidos de forma pareja en el día. */
    public List<Integer> indicesWeb(int n) {
        int k = cuposWeb(n);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            indices.add((int) Math.floor((double) i * n / k));
        }
        return indices;
    }

    /** RN-14: NULL significa sin tope. */
    public boolean superaTope(long reservasActivasEnLinea) {
        return tope != null && reservasActivasEnLinea >= tope;
    }

    public Integer getPorcentaje() { return porcentaje; }
    public Integer getTope() { return tope; }
}
