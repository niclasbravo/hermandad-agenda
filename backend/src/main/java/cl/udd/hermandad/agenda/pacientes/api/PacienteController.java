package cl.udd.hermandad.agenda.pacientes.api;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.pacientes.api.PacienteDtos.*;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DocumentoService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.CanalRegistro;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** M3 · CU-03. TENS/TONS escribe; la Administración solo lee. */
@RestController
@RequestMapping("/api")
public class PacienteController {

    private final PacienteService servicio;
    private final DocumentoService documentos;
    private final Reloj reloj;

    public PacienteController(PacienteService servicio, DocumentoService documentos, Reloj reloj) {
        this.servicio = servicio;
        this.documentos = documentos;
        this.reloj = reloj;
    }

    @GetMapping("/pacientes")
    public PacienteDto buscar(@RequestParam String documento) {
        return servicio.buscar(documento).map(p -> PacienteDto.de(p, reloj.hoy()))
                .orElseThrow(() -> new ErrorNegocio(ErrorNegocio.Tipo.NO_ENCONTRADO, "PACIENTE_NO_ENCONTRADO",
                        "No encontramos un paciente con el documento " + documento.trim() + ".",
                        Map.of("ofrecerRegistro", true)));
    }

    @GetMapping("/pacientes/{id}")
    public PacienteDto obtener(@PathVariable Integer id) {
        return PacienteDto.de(servicio.obtener(id), reloj.hoy());
    }

    @PostMapping("/pacientes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENS')")
    public PacienteDto registrar(@Valid @RequestBody PacienteRequest r) {
        return PacienteDto.de(servicio.registrar(r.datos(), CanalRegistro.TENS), reloj.hoy());
    }

    @PutMapping("/pacientes/{id}")
    @PreAuthorize("hasRole('TENS')")
    public PacienteDto actualizar(@PathVariable Integer id, @Valid @RequestBody PacienteRequest r) {
        return PacienteDto.de(servicio.actualizar(id, r.datos()), reloj.hoy());
    }

    @PostMapping("/pacientes/{id}/verificaciones")
    @PreAuthorize("hasRole('TENS')")
    public PacienteDto verificar(@PathVariable Integer id, @Valid @RequestBody VerificacionRequest r) {
        servicio.registrarVerificacion(id, r.fechaVerificacion(), r.cesfamInscripcion(), r.fonasaVigente(),
                r.inscritoCesfam(), r.observacion(), r.idsDocumento());
        return PacienteDto.de(servicio.obtener(id), reloj.hoy());
    }

    @GetMapping("/pacientes/{id}/documentos")
    public List<DocumentoDto> documentos(@PathVariable Integer id) {
        return servicio.documentos(id).stream().map(DocumentoDto::de).toList();
    }

    /** Solo personal con sesión (la ruta está bajo /api, que exige sesión). */
    @GetMapping("/documentos/{id}/archivo")
    public ResponseEntity<InputStreamResource> archivo(@PathVariable Integer id) {
        DocumentoService.Archivo a = documentos.abrir(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + a.documento().getNombreArchivo().replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(a.documento().getTipoMime()))
                .body(new InputStreamResource(a.contenido()));
    }
}
