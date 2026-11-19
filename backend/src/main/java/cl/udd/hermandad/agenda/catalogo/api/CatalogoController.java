package cl.udd.hermandad.agenda.catalogo.api;

import cl.udd.hermandad.agenda.catalogo.api.CatalogoDtos.*;
import cl.udd.hermandad.agenda.catalogo.aplicacion.CatalogoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** M1 · CU-01. La lectura la usan ambos perfiles (filtros); la escritura es solo de la Administración. */
@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CatalogoService servicio;

    public CatalogoController(CatalogoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/policlinicos")
    public List<PoliclinicoDto> policlinicos() {
        return servicio.listarPoliclinicos().stream().map(PoliclinicoDto::de).toList();
    }

    @PostMapping("/policlinicos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public PoliclinicoDto crearPoliclinico(@Valid @RequestBody PoliclinicoRequest r) {
        return PoliclinicoDto.de(servicio.crearPoliclinico(r.nombre(), r.direccion(), r.comuna(), r.boxes()));
    }

    @PutMapping("/policlinicos/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public PoliclinicoDto actualizarPoliclinico(@PathVariable Integer id, @Valid @RequestBody PoliclinicoRequest r) {
        return PoliclinicoDto.de(servicio.actualizarPoliclinico(id, r.nombre(), r.direccion(), r.comuna(), r.activo()));
    }

    @PostMapping("/policlinicos/{id}/box")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public PoliclinicoDto agregarBox(@PathVariable Integer id, @Valid @RequestBody BoxRequest r) {
        servicio.agregarBox(id, r.nombre());
        return PoliclinicoDto.de(servicio.policlinico(id));
    }

    @PutMapping("/box/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public BoxDto actualizarBox(@PathVariable Integer id, @Valid @RequestBody BoxRequest r) {
        return BoxDto.de(servicio.actualizarBox(id, r.nombre(), r.activo() == null || r.activo()));
    }

    @GetMapping("/profesiones")
    public List<ProfesionDto> profesiones() {
        return servicio.listarProfesiones().stream().map(ProfesionDto::de).toList();
    }

    @PutMapping("/profesiones/{id}/duracion")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ProfesionDto definirDuracion(@PathVariable Integer id, @RequestBody DuracionRequest r) {
        return ProfesionDto.de(servicio.definirDuracion(id, r.duracionMin(), r.duracionAlumnoMin()));
    }

    @GetMapping("/profesionales")
    public List<ProfesionalDto> profesionales() {
        return servicio.listarProfesionales().stream().map(ProfesionalDto::de).toList();
    }

    @GetMapping("/profesionales/{id}")
    public ProfesionalDto profesional(@PathVariable Integer id) {
        return ProfesionalDto.de(servicio.profesional(id));
    }

    @PostMapping("/profesionales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ProfesionalDto registrar(@Valid @RequestBody ProfesionalRequest r) {
        return ProfesionalDto.de(servicio.registrarProfesional(r.datos()));
    }

    @PutMapping("/profesionales/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ProfesionalDto actualizar(@PathVariable Integer id, @Valid @RequestBody ProfesionalRequest r) {
        return ProfesionalDto.de(servicio.actualizarProfesional(id, r.datos()));
    }

    @PostMapping("/profesionales/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ProfesionalDto desactivar(@PathVariable Integer id) {
        return ProfesionalDto.de(servicio.desactivarProfesional(id));
    }

    @PutMapping("/profesionales/{id}/publicacion")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public ProfesionalDto publicacion(@PathVariable Integer id, @Valid @RequestBody PublicacionRequest r) {
        return ProfesionalDto.de(servicio.configurarPublicacion(id, r.plazoPublicacionDias()));
    }
}
