package cl.udd.hermandad.agenda.seguridad.aplicacion;

import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.compartido.UsuarioSesion;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import cl.udd.hermandad.agenda.seguridad.dominio.IAuditoriaRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.RegistroAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** RNF-05. Registra quién hizo qué y cuándo. Si no hay sesión, el usuario queda NULL (sistema o paciente en línea). */
@Service
public class AuditoriaService {

    private final IAuditoriaRepository repositorio;
    private final Reloj reloj;

    public AuditoriaService(IAuditoriaRepository repositorio, Reloj reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AccionAuditoria accion, String entidad, long idRegistro, String detalle) {
        Integer idUsuario = SesionActual.opcional().map(UsuarioSesion::idUsuario).orElse(null);
        registrarComo(idUsuario, accion, entidad, idRegistro, detalle);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarComo(Integer idUsuario, AccionAuditoria accion, String entidad, long idRegistro, String detalle) {
        repositorio.save(new RegistroAuditoria(idUsuario, reloj.ahora(), accion, entidad, idRegistro, detalle));
    }

    @Transactional(readOnly = true)
    public List<RegistroAuditoria> historial(String entidad, long idRegistro) {
        return repositorio.findByEntidadAndIdRegistroOrderByIdAsc(entidad, idRegistro);
    }
}
