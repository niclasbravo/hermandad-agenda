package cl.udd.hermandad.agenda.seguridad.infraestructura;

import cl.udd.hermandad.agenda.seguridad.dominio.IAuditoriaRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.RegistroAuditoria;
import org.springframework.data.repository.Repository;

public interface AuditoriaRepositoryJpa extends Repository<RegistroAuditoria, Long>, IAuditoriaRepository {
}
