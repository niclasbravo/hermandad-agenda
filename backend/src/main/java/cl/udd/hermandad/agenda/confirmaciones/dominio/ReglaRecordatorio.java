package cl.udd.hermandad.agenda.confirmaciones.dominio;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Recordatorio: el día anterior a la cita a la hora configurada (10:00). Si ese momento ya pasó, no se programa.
 * Refuerzos: N horas antes de la cita, solo los que quedan en el futuro (D-04).
 */
public class ReglaRecordatorio {

    private final LocalTime horaRecordatorio;
    private final List<Integer> horasRefuerzo;

    public ReglaRecordatorio(LocalTime horaRecordatorio, List<Integer> horasRefuerzo) {
        this.horaRecordatorio = horaRecordatorio == null ? LocalTime.of(10, 0) : horaRecordatorio;
        this.horasRefuerzo = horasRefuerzo == null ? List.of() : horasRefuerzo;
    }

    public Optional<LocalDateTime> momentoRecordatorio(LocalDateTime inicioCita, LocalDateTime ahora) {
        LocalDateTime momento = LocalDateTime.of(inicioCita.toLocalDate().minusDays(1), horaRecordatorio);
        return momento.isAfter(ahora) ? Optional.of(momento) : Optional.empty();
    }

    public List<LocalDateTime> momentosRefuerzo(LocalDateTime inicioCita, LocalDateTime ahora) {
        List<LocalDateTime> momentos = new ArrayList<>();
        for (Integer h : horasRefuerzo) {
            LocalDateTime m = inicioCita.minusHours(h);
            if (m.isAfter(ahora) && !momentos.contains(m)) {
                momentos.add(m);
            }
        }
        return momentos;
    }
}
