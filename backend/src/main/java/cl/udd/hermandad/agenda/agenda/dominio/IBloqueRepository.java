package cl.udd.hermandad.agenda.agenda.dominio;

import java.util.List;
import java.util.Optional;

public interface IBloqueRepository {
    BloqueHorario guardar(BloqueHorario bloque);
    Optional<BloqueHorario> buscar(Integer id);
    /** Bloques activos que se superponen (D-09). */
    List<BloqueHorario> buscarSuperpuestos(BloqueHorario bloque);
    List<BloqueHorario> activosDelProfesional(Integer idProfesional);
    List<BloqueHorario> activos();
}
