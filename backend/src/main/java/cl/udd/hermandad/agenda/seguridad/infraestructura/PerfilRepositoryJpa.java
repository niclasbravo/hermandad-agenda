package cl.udd.hermandad.agenda.seguridad.infraestructura;

import cl.udd.hermandad.agenda.seguridad.dominio.IPerfilRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.Perfil;
import org.springframework.data.repository.Repository;

public interface PerfilRepositoryJpa extends Repository<Perfil, Integer>, IPerfilRepository {
}
