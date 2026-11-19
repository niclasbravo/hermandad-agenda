package cl.udd.hermandad.agenda.catalogo.api;

import cl.udd.hermandad.agenda.catalogo.aplicacion.CatalogoService;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesion;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/** API pública (sin sesión): solo nombre, dirección y comuna de los policlínicos, y profesiones con duración. */
@RestController
@RequestMapping("/api/publico")
public class CatalogoPublicoController {

    public record PoliclinicoPublico(Integer id, String nombre, String direccion, String comuna, List<String> profesiones) {
    }

    public record ProfesionPublica(Integer id, String nombre) {
    }

    public record ProfesionalPublico(Integer id, String nombre, Integer idProfesion, List<Integer> idsPoliclinico) {
    }

    private final CatalogoService servicio;

    public CatalogoPublicoController(CatalogoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/policlinicos")
    public List<PoliclinicoPublico> policlinicos() {
        List<Profesional> activos = servicio.listarProfesionales().stream().filter(Profesional::isActivo).toList();
        return servicio.listarPoliclinicos().stream().filter(p -> p.isActivo())
                .map(p -> new PoliclinicoPublico(p.getId(), p.getNombre(), p.getDireccion(), p.getComuna(),
                        activos.stream().filter(pr -> pr.atiendeEn(p.getId()) && pr.duracionAtencion() != null)
                                .map(pr -> pr.getProfesion().getNombre()).distinct().sorted().toList()))
                .toList();
    }

    @GetMapping("/profesiones")
    public List<ProfesionPublica> profesiones() {
        return servicio.listarProfesiones().stream().filter(Profesion::tieneDuracion)
                .map(p -> new ProfesionPublica(p.getId(), p.getNombre())).toList();
    }

    @GetMapping("/profesionales")
    public List<ProfesionalPublico> profesionales() {
        return servicio.listarProfesionales().stream().filter(Profesional::isActivo)
                .filter(p -> p.duracionAtencion() != null)
                .sorted(Comparator.comparing(Profesional::getApellidos))
                .map(p -> new ProfesionalPublico(p.getId(), p.nombreCompleto(), p.getProfesion().getId(),
                        p.policlinicosOrdenados().stream().map(x -> x.getId()).toList()))
                .toList();
    }
}
