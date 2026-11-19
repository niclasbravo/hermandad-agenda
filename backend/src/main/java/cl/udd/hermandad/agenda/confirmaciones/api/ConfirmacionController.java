package cl.udd.hermandad.agenda.confirmaciones.api;

import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.dominio.LlamadaConfirmacion;
import cl.udd.hermandad.agenda.confirmaciones.dominio.ResultadoLlamada;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** RF-38 y RF-39 (TENS/TONS). */
@RestController
@RequestMapping("/api/citas/{id}")
public class ConfirmacionController {

    public record LlamadaRequest(@NotNull(message = "Elige el resultado de la llamada.") ResultadoLlamada resultado,
                                 @Size(max = 500) String comentario) {
    }

    public record LlamadaDto(Long id, ResultadoLlamada resultado, String comentario, LocalDateTime fechaHora) {
        static LlamadaDto de(LlamadaConfirmacion l) {
            return new LlamadaDto(l.getId(), l.getResultado(), l.getComentario(), l.getFechaHora());
        }
    }

    private final NotificacionService servicio;

    public ConfirmacionController(NotificacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/reenviar-recordatorio")
    @PreAuthorize("hasRole('TENS')")
    public Map<String, Object> reenviar(@PathVariable Long id) {
        servicio.reenviarRecordatorio(id);
        return Map.of("mensaje", "Recordatorio reenviado por WhatsApp. Saldrá en menos de un minuto.");
    }

    @PostMapping("/llamadas")
    @PreAuthorize("hasRole('TENS')")
    public LlamadaDto llamada(@PathVariable Long id, @Valid @RequestBody LlamadaRequest r) {
        return LlamadaDto.de(servicio.registrarLlamada(id, r.resultado(), r.comentario()));
    }
}
