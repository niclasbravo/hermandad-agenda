package cl.udd.hermandad.agenda.catalogo.infraestructura;

import cl.udd.hermandad.agenda.catalogo.dominio.Box;
import cl.udd.hermandad.agenda.catalogo.dominio.IPoliclinicoRepository;
import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PoliclinicoRepositoryJpa extends Repository<Policlinico, Integer>, IPoliclinicoRepository {

    @Override
    @Query("select b from Box b join fetch b.policlinico where b.id = :idBox")
    Optional<Box> buscarBox(@Param("idBox") Integer idBox);
}
