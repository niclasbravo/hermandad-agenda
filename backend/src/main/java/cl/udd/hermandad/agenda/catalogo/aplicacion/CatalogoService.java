package cl.udd.hermandad.agenda.catalogo.aplicacion;

import cl.udd.hermandad.agenda.catalogo.dominio.*;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** CU-01: policlínicos, box, profesiones y su duración, y profesionales. */
@Service
@Transactional
public class CatalogoService {

    public record DatosProfesional(Integer idProfesion, String nombres, String apellidos, boolean esAlumnoPractica,
                                   String tituloProfesional, String casaEstudios, String registroPrestador,
                                   List<Integer> idsPoliclinico) {
    }

    private final IPoliclinicoRepository policlinicos;
    private final IProfesionRepository profesiones;
    private final IProfesionalRepository profesionales;
    private final AuditoriaService auditoria;

    public CatalogoService(IPoliclinicoRepository policlinicos, IProfesionRepository profesiones,
                           IProfesionalRepository profesionales, AuditoriaService auditoria) {
        this.policlinicos = policlinicos;
        this.profesiones = profesiones;
        this.profesionales = profesionales;
        this.auditoria = auditoria;
    }

    // ---------- Policlínicos y box (RF-01) ----------

    @Transactional(readOnly = true)
    public List<Policlinico> listarPoliclinicos() {
        List<Policlinico> lista = policlinicos.findAllByOrderByIdAsc();
        lista.forEach(p -> p.getBoxes().size());
        return lista;
    }

    @Transactional(readOnly = true)
    public Policlinico policlinico(Integer id) {
        Policlinico p = policlinicos.findById(id)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese policlínico."));
        p.getBoxes().size();
        return p;
    }

    public Policlinico crearPoliclinico(String nombre, String direccion, String comuna, List<String> boxes) {
        Policlinico p = new Policlinico(nombre, direccion, comuna);
        if (boxes != null) {
            boxes.stream().filter(b -> b != null && !b.isBlank()).distinct().forEach(p::agregarBox);
        }
        p = policlinicos.save(p);
        auditoria.registrar(AccionAuditoria.CREAR, "policlinico", p.getId(), p.getNombre());
        return p;
    }

    public Policlinico actualizarPoliclinico(Integer id, String nombre, String direccion, String comuna, Boolean activo) {
        Policlinico p = policlinico(id);
        p.actualizar(nombre, direccion, comuna);
        if (activo != null) {
            p.setActivo(activo);
        }
        auditoria.registrar(AccionAuditoria.MODIFICAR, "policlinico", p.getId(), p.getNombre());
        return p;
    }

    public Box agregarBox(Integer idPoliclinico, String nombre) {
        Policlinico p = policlinico(idPoliclinico);
        boolean repetido = p.getBoxes().stream().anyMatch(b -> b.getNombre().equalsIgnoreCase(nombre.trim()));
        if (repetido) {
            throw ErrorNegocio.conflicto("BOX_REPETIDO", "Este policlínico ya tiene un box con ese nombre.");
        }
        Box box = p.agregarBox(nombre);
        policlinicos.save(p);
        auditoria.registrar(AccionAuditoria.CREAR, "box", box.getId() == null ? 0 : box.getId(), p.getNombre() + " · " + nombre);
        return box;
    }

    public Box actualizarBox(Integer idBox, String nombre, boolean activo) {
        Box box = box(idBox);
        box.actualizar(nombre, activo);
        auditoria.registrar(AccionAuditoria.MODIFICAR, "box", box.getId(), nombre);
        return box;
    }

    @Transactional(readOnly = true)
    public Box box(Integer idBox) {
        return policlinicos.buscarBox(idBox).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese box."));
    }

    // ---------- Profesiones y duración (RF-05) ----------

    @Transactional(readOnly = true)
    public List<Profesion> listarProfesiones() {
        return profesiones.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Profesion profesion(Integer id) {
        return profesiones.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos esa profesión."));
    }

    public Profesion definirDuracion(Integer idProfesion, Integer minutos, Integer minutosAlumno) {
        Profesion p = profesion(idProfesion);
        p.definirDuracion(minutos, minutosAlumno);
        auditoria.registrar(AccionAuditoria.MODIFICAR, "profesion", p.getId(),
                p.getNombre() + ": " + minutos + " min" + (minutosAlumno == null ? "" : ", alumnos " + minutosAlumno + " min"));
        return p;
    }

    // ---------- Profesionales (RF-02, RF-03, RF-08) ----------

    @Transactional(readOnly = true)
    public List<Profesional> listarProfesionales() {
        List<Profesional> lista = profesionales.findAllByOrderByApellidosAscNombresAsc();
        lista.forEach(p -> p.getPoliclinicos().size());
        return lista;
    }

    @Transactional(readOnly = true)
    public Profesional profesional(Integer id) {
        Profesional p = profesionales.findById(id)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese profesional."));
        p.getPoliclinicos().size();
        return p;
    }

    public Profesional registrarProfesional(DatosProfesional d) {
        Profesional p = new Profesional(profesion(d.idProfesion()), d.nombres(), d.apellidos(), d.esAlumnoPractica(),
                new Credencial(d.tituloProfesional(), d.casaEstudios(), d.registroPrestador()), policlinicosDe(d.idsPoliclinico()));
        p = profesionales.save(p);
        auditoria.registrar(AccionAuditoria.CREAR, "profesional", p.getId(), p.nombreCompleto());
        return p;
    }

    public Profesional actualizarProfesional(Integer id, DatosProfesional d) {
        Profesional p = profesional(id);
        p.actualizar(profesion(d.idProfesion()), d.nombres(), d.apellidos(), d.esAlumnoPractica(),
                new Credencial(d.tituloProfesional(), d.casaEstudios(), d.registroPrestador()), policlinicosDe(d.idsPoliclinico()));
        auditoria.registrar(AccionAuditoria.MODIFICAR, "profesional", p.getId(), p.nombreCompleto());
        return p;
    }

    public Profesional desactivarProfesional(Integer id) {
        Profesional p = profesional(id);
        p.desactivar();
        auditoria.registrar(AccionAuditoria.MODIFICAR, "profesional", p.getId(), "Desactivado");
        return p;
    }

    public Profesional configurarPublicacion(Integer id, int dias) {
        Profesional p = profesional(id);
        p.configurarPublicacion(dias);
        auditoria.registrar(AccionAuditoria.MODIFICAR, "profesional", p.getId(), "Plazo de publicación: " + dias + " días");
        return p;
    }

    /** CU-01 E3: aviso cuando la profesión no tiene duración. */
    public static Optional<String> avisoDuracion(Profesional p) {
        if (p.duracionAtencion() == null) {
            return Optional.of("No se generarán cupos hasta definir la duración de " + p.getProfesion().getNombre() + ".");
        }
        return Optional.empty();
    }

    private Set<Policlinico> policlinicosDe(List<Integer> ids) {
        Set<Policlinico> set = new LinkedHashSet<>();
        if (ids != null) {
            for (Integer id : ids) {
                if (id == null) {
                    throw ErrorNegocio.validacion("idsPoliclinico", "Elige un policlínico válido.");
                }
                set.add(policlinicos.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese policlínico.")));
            }
        }
        return set;
    }
}
