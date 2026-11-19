package cl.udd.hermandad.agenda.confirmaciones.api;

import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.RedactorMensajes;
import cl.udd.hermandad.agenda.confirmaciones.dominio.Mensaje;
import cl.udd.hermandad.agenda.confirmaciones.dominio.Respuesta;
import cl.udd.hermandad.agenda.pacientes.dominio.Telefono;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Simulador de WhatsApp (sección 11.6). Solo existe en los perfiles demo y dev. */
@RestController
@RequestMapping("/api/dev/simulador")
@Profile({"demo", "dev"})
public class SimuladorController {

    public record RespuestaRequest(@NotNull Long idMensaje, @NotNull Respuesta respuesta) {
    }

    public record TelefonoDto(String telefono, String formateado) {
    }

    public record BurbujaDto(Long idMensaje, Long idCita, String tipo, String texto, boolean conBotones, String estado,
                             LocalDateTime fechaEnvio, boolean citaVigente) {
    }

    private final NotificacionService servicio;

    public SimuladorController(NotificacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/telefonos")
    public List<TelefonoDto> telefonos() {
        return servicio.telefonosConMensajes().stream().map(t -> new TelefonoDto(t, Telefono.formatear(t))).toList();
    }

    @GetMapping("/mensajes")
    @Transactional(readOnly = true)
    public List<BurbujaDto> mensajes(@RequestParam String telefono) {
        List<Mensaje> lista = servicio.enviadosA(telefono);
        return lista.reversed().stream().map(m -> new BurbujaDto(m.getId(), m.getCita().getId(),
                m.getPlantilla().getTipo().name(), RedactorMensajes.texto(m.getPlantilla(), m.getCita(), m.getDestinatario()),
                m.getPlantilla().llevaBotones(), m.getEstadoEntrega().name(), m.getFechaEnvio(), m.getCita().estaVigente()))
                .toList();
    }

    /** Usa el mismo NotificacionService.procesarRespuesta que el webhook. */
    @PostMapping("/respuesta")
    public Map<String, Object> responder(@Valid @RequestBody RespuestaRequest r) {
        servicio.procesarRespuesta(r.idMensaje(), r.respuesta());
        servicio.enviarPendientes();
        return Map.of("ok", true);
    }

    @PostMapping("/recordatorio/{idCita}")
    public Map<String, Object> forzarRecordatorio(@PathVariable Long idCita) {
        servicio.forzarRecordatorio(idCita);
        int enviados = servicio.enviarPendientes();
        return Map.of("enviados", enviados);
    }

    @PostMapping("/enviar-pendientes")
    public Map<String, Object> enviarPendientes() {
        return Map.of("enviados", servicio.enviarPendientes());
    }
}
