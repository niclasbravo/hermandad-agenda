package cl.udd.hermandad.agenda.seguridad.dominio;

import java.util.List;
import java.util.Optional;

public interface IPerfilRepository {
    Perfil save(Perfil perfil);
    Optional<Perfil> findByNombre(String nombre);
    List<Perfil> findAll();
}
