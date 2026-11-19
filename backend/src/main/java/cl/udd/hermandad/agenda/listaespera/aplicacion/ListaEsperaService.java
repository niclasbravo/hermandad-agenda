package cl.udd.hermandad.agenda.listaespera.aplicacion;

import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.agenda.dominio.ICupoRepository;
import cl.udd.hermandad.agenda.catalogo.aplicacion.CatalogoService;
import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesion;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.listaespera.dominio.*;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CU-04: lista de espera (RF-25, RF-26, RF-42, RF-43). Sin priorización (RN-06). */
@Service
@Transactional
public class ListaEsperaService {

    public record AvisoVista(AvisoCupoLiberado aviso, Cupo cupo, InscripcionEspera primera) {
    }

    private final IListaEsperaRepository repositorio;
    private final ICupoRepository cupos;
    private final PacienteService pacientes;
    private final CatalogoService catalogo;
    private final AuditoriaService auditoria;
    private final Reloj reloj;

    public ListaEsperaService(IListaEsperaRepository repositorio, ICupoRepository cupos, PacienteService pacientes,
                              CatalogoService catalogo, AuditoriaService auditoria, Reloj reloj) {
        this.repositorio = repositorio;
        this.cupos = cupos;
        this.pacientes = pacientes;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public InscripcionEspera inscribir(Integer idPaciente, Integer idPoliclinico, Integer idProfesion) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        Paciente p = pacientes.obtener(idPaciente);
        Policlinico pol = catalogo.policlinico(idPoliclinico);
        Profesion prof = catalogo.profesion(idProfesion);
        boolean yaEsta = repositorio.enEsperaDelPaciente(idPaciente).stream()
                .anyMatch(i -> i.getPoliclinico().getId().equals(idPoliclinico) && i.getProfesion().getId().equals(idProfesion));
        if (yaEsta) {
            throw ErrorNegocio.conflicto("YA_EN_LISTA", p.nombreCompleto() + " ya está en la lista de espera de "
                    + prof.getNombre() + " en " + pol.getNombre() + ".");
        }
        InscripcionEspera i = repositorio.guardar(new InscripcionEspera(p, pol, prof, idUsuario, reloj.ahora()));
        auditoria.registrar(AccionAuditoria.CREAR, "inscripcion_espera", i.getId(),
                "Paciente " + idPaciente + " en " + prof.getNombre() + ", " + pol.getNombre());
        return i;
    }

    @Transactional(readOnly = true)
    public List<InscripcionEspera> listar(Integer idPoliclinico, Integer idProfesion) {
        return repositorio.enEspera(idPoliclinico, idProfesion);
    }

    @Transactional(readOnly = true)
    public long contarEnEspera(Integer idPoliclinico) {
        return repositorio.contarEnEspera(idPoliclinico);
    }

    @Transactional(readOnly = true)
    public List<InscripcionEspera> delPaciente(Integer idPaciente) {
        return repositorio.enEsperaDelPaciente(idPaciente);
    }

    /** RF-42: queda donde la TENS/TONS indique (por defecto, primero). */
    public InscripcionEspera adelantar(Integer idInscripcion, String motivo, Integer posicion) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        InscripcionEspera i = inscripcion(idInscripcion);
        exigirPoliclinicoPropio(i);
        List<InscripcionEspera> lista = repositorio.enEspera(i.getPoliclinico().getId(), i.getProfesion().getId());
        int actual = lista.indexOf(i);
        int destino = posicion == null || posicion < 1 ? 0 : Math.min(posicion, lista.size()) - 1;
        if (actual <= destino) {
            throw ErrorNegocio.regla("SIN_CAMBIO", "Esta persona ya está en esa posición o más adelante.");
        }
        LocalDateTime referencia = lista.get(destino).getFechaOrden();
        LocalDateTime nueva = referencia.minusSeconds(1);
        if (destino > 0 && !lista.get(destino - 1).getFechaOrden().isBefore(nueva)) {
            nueva = referencia;
        }
        i.adelantar(motivo, nueva, idUsuario, reloj.ahora());
        repositorio.guardar(i);
        auditoria.registrar(AccionAuditoria.ADELANTAR, "inscripcion_espera", i.getId(), motivo);
        return i;
    }

    /** RF-43. */
    public InscripcionEspera retirar(Integer idInscripcion, MotivoRetiro motivo, String detalle, Long idCita) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        InscripcionEspera i = inscripcion(idInscripcion);
        exigirPoliclinicoPropio(i);
        i.retirar(motivo, detalle, idCita, idUsuario, reloj.ahora());
        repositorio.guardar(i);
        auditoria.registrar(AccionAuditoria.RETIRAR, "inscripcion_espera", i.getId(),
                motivo + (detalle == null ? "" : ": " + detalle));
        return i;
    }

    /** RF-26: si hay pacientes esperando esa atención en ese policlínico, avisa a la TENS/TONS. */
    public Optional<AvisoCupoLiberado> avisarCupoLiberado(Cupo cupo, Long idCitaAnulada) {
        if (!cupo.inicio().isAfter(reloj.ahora())) {
            return Optional.empty();
        }
        Integer pol = cupo.getBloque().getBox().getPoliclinico().getId();
        Integer prof = cupo.getBloque().getProfesional().getProfesion().getId();
        if (repositorio.enEspera(pol, prof).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(repositorio.guardarAviso(new AvisoCupoLiberado(cupo, idCitaAnulada, reloj.ahora())));
    }

    /** Avisos sin atender cuyo cupo sigue libre y con alguien esperando. */
    @Transactional(readOnly = true)
    public List<AvisoVista> avisosPendientes(Integer idPoliclinico) {
        List<AvisoVista> vistas = new ArrayList<>();
        LocalDateTime ahora = reloj.ahora();
        for (AvisoCupoLiberado a : repositorio.avisosPendientes(idPoliclinico)) {
            Cupo c = a.getCupo();
            if (c.getBloqueo() != null || !c.inicio().isAfter(ahora) || cupos.tieneCitaVigente(c.getId())) {
                continue;
            }
            List<InscripcionEspera> lista = repositorio.enEspera(c.getBloque().getBox().getPoliclinico().getId(),
                    c.getBloque().getProfesional().getProfesion().getId());
            if (!lista.isEmpty()) {
                vistas.add(new AvisoVista(a, c, lista.get(0)));
            }
        }
        return vistas;
    }

    public AvisoCupoLiberado atenderAviso(Integer idAviso) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        AvisoCupoLiberado a = repositorio.buscarAviso(idAviso)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese aviso."));
        a.atender(idUsuario);
        repositorio.guardarAviso(a);
        return a;
    }

    private InscripcionEspera inscripcion(Integer id) {
        return repositorio.buscar(id).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos esa inscripción."));
    }

    private void exigirPoliclinicoPropio(InscripcionEspera i) {
        var u = SesionActual.usuario();
        if (u.esTens() && !i.getPoliclinico().getId().equals(u.idPoliclinicoBase())) {
            throw ErrorNegocio.sinPermiso("Esta inscripción es de otro policlínico.");
        }
    }
}
