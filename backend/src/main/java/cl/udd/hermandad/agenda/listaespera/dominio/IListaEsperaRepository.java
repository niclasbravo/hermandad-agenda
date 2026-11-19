package cl.udd.hermandad.agenda.listaespera.dominio;

import java.util.List;
import java.util.Optional;

public interface IListaEsperaRepository {
    InscripcionEspera guardar(InscripcionEspera inscripcion);
    Optional<InscripcionEspera> buscar(Integer id);
    /** EN_ESPERA ordenadas por fecha_orden; los filtros nulos no se aplican. */
    List<InscripcionEspera> enEspera(Integer idPoliclinico, Integer idProfesion);
    List<InscripcionEspera> enEsperaDelPaciente(Integer idPaciente);
    long contarEnEspera(Integer idPoliclinico);
    AvisoCupoLiberado guardarAviso(AvisoCupoLiberado aviso);
    Optional<AvisoCupoLiberado> buscarAviso(Integer id);
    List<AvisoCupoLiberado> avisosPendientes(Integer idPoliclinico);
}
