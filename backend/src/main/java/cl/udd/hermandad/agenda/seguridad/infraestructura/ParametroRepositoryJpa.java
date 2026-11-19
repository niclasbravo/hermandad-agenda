package cl.udd.hermandad.agenda.seguridad.infraestructura;

import cl.udd.hermandad.agenda.seguridad.dominio.IParametroRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.ParametroSistema;
import org.springframework.data.repository.Repository;

public interface ParametroRepositoryJpa extends Repository<ParametroSistema, String>, IParametroRepository {
}
