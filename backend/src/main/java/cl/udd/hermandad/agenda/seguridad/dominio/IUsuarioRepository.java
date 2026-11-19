package cl.udd.hermandad.agenda.seguridad.dominio;

import java.util.List;
import java.util.Optional;

public interface IUsuarioRepository {
    Usuario save(Usuario usuario);
    Optional<Usuario> findById(Integer id);
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);
    List<Usuario> findAll();
    long count();
}
