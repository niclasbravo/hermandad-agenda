package cl.udd.hermandad.agenda.compartido;

import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Programador de tareas: cada minuto envía los mensajes pendientes (RNF-09: confirmación en 5 min o menos). */
@Component
@ConditionalOnProperty(name = "agenda.programador.habilitado", havingValue = "true", matchIfMissing = true)
public class ProgramadorTareas {

    private final NotificacionService notificaciones;

    public ProgramadorTareas(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void enviarMensajes() {
        notificaciones.enviarPendientes();
    }
}
