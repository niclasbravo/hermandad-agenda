package cl.udd.hermandad.agenda.catalogo.dominio;

import java.util.List;
import java.util.Optional;

public interface IPoliclinicoRepository {
    Policlinico save(Policlinico policlinico);
    Optional<Policlinico> findById(Integer id);
    List<Policlinico> findAllByOrderByIdAsc();
    Optional<Box> buscarBox(Integer idBox);
}
