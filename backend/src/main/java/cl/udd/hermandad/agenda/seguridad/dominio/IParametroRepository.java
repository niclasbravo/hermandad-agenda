package cl.udd.hermandad.agenda.seguridad.dominio;

import java.util.List;
import java.util.Optional;

public interface IParametroRepository {
    ParametroSistema save(ParametroSistema parametro);
    Optional<ParametroSistema> findById(String clave);
    List<ParametroSistema> findAllByOrderByClaveAsc();
}
