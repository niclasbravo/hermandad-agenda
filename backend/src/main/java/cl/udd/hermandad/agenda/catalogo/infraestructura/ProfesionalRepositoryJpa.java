package cl.udd.hermandad.agenda.catalogo.infraestructura;

import cl.udd.hermandad.agenda.catalogo.dominio.IProfesionalRepository;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesional;
import org.springframework.data.repository.Repository;

public interface ProfesionalRepositoryJpa extends Repository<Profesional, Integer>, IProfesionalRepository {
}
