package cl.udd.hermandad.agenda.seguridad.infraestructura;

import cl.udd.hermandad.agenda.seguridad.dominio.IUsuarioRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.Usuario;
import org.springframework.data.repository.Repository;

public interface UsuarioRepositoryJpa extends Repository<Usuario, Integer>, IUsuarioRepository {
}
