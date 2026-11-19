package cl.udd.hermandad.agenda.seguridad.api;

import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import cl.udd.hermandad.agenda.seguridad.dominio.ParametroSistema;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parametros")
public class ParametroController {

    public record ParametroDto(String clave, String valor, String tipoDato, String descripcion) {
        static ParametroDto de(ParametroSistema p) {
            return new ParametroDto(p.getClave(), p.getValor(), p.getTipoDato().name(), p.getDescripcion());
        }
    }

    private final ParametroService servicio;

    public ParametroController(ParametroService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ParametroDto> listar() {
        return servicio.listar().stream().map(ParametroDto::de).toList();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public List<ParametroDto> actualizar(@RequestBody Map<String, String> valores) {
        return servicio.actualizar(valores).stream().map(ParametroDto::de).toList();
    }
}
