package cl.udd.hermandad.agenda.catalogo.dominio;

import java.util.List;
import java.util.Optional;

public interface IProfesionRepository {
    Profesion save(Profesion profesion);
    Optional<Profesion> findById(Integer id);
    List<Profesion> findAllByOrderByNombreAsc();
}
