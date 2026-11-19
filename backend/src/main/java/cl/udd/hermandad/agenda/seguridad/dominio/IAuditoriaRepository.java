package cl.udd.hermandad.agenda.seguridad.dominio;

import java.util.List;

public interface IAuditoriaRepository {
    RegistroAuditoria save(RegistroAuditoria registro);
    List<RegistroAuditoria> findByEntidadAndIdRegistroOrderByIdAsc(String entidad, Long idRegistro);
}
