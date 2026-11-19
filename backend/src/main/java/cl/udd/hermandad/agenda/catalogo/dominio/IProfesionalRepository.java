package cl.udd.hermandad.agenda.catalogo.dominio;

import java.util.List;
import java.util.Optional;

public interface IProfesionalRepository {
    Profesional save(Profesional profesional);
    Optional<Profesional> findById(Integer id);
    List<Profesional> findAllByOrderByApellidosAscNombresAsc();
}
