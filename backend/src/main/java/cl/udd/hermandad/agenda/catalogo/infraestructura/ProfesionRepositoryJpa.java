package cl.udd.hermandad.agenda.catalogo.infraestructura;

import cl.udd.hermandad.agenda.catalogo.dominio.IProfesionRepository;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesion;
import org.springframework.data.repository.Repository;

public interface ProfesionRepositoryJpa extends Repository<Profesion, Integer>, IProfesionRepository {
}
