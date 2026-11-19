package cl.udd.hermandad.agenda.confirmaciones.infraestructura;

import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.dominio.EstadoEntrega;
import cl.udd.hermandad.agenda.confirmaciones.dominio.IServicioMensajeria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simulador de WhatsApp (perfil por defecto). Genera un id_externo falso y, unos segundos después, simula el aviso
 * ENTREGADO llamando al mismo NotificacionService.procesarEstado. Si el teléfono termina en 0000, el envío falla.
 */
public class SimuladorMensajeriaAdapter implements IServicioMensajeria {

    private static final Logger log = LoggerFactory.getLogger(SimuladorMensajeriaAdapter.class);

    private final ObjectProvider<NotificacionService> notificaciones;
    private final long demoraSegundos;
    private final ScheduledExecutorService programador = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "simulador-whatsapp");
        t.setDaemon(true);
        return t;
    });

    public SimuladorMensajeriaAdapter(ObjectProvider<NotificacionService> notificaciones, long demoraSegundos) {
        this.notificaciones = notificaciones;
        this.demoraSegundos = demoraSegundos;
    }

    @Override
    public String enviarPlantilla(String telefono, String plantilla, List<String> parametros, List<String> botones) {
        if (telefono != null && telefono.endsWith("0000")) {
            throw new ErrorEnvioMensaje("Número sin WhatsApp (simulado)");
        }
        String id = "sim." + UUID.randomUUID();
        log.info("Simulador: {} enviado a ***{}", plantilla, telefono == null ? "" : telefono.substring(Math.max(0, telefono.length() - 4)));
        if (demoraSegundos >= 0) {
            programador.schedule(() -> {
                try {
                    notificaciones.getObject().procesarEstado(id, EstadoEntrega.ENTREGADO);
                } catch (Exception e) {
                    log.warn("Simulador: no se pudo marcar entregado", e);
                }
            }, demoraSegundos, TimeUnit.SECONDS);
        }
        return id;
    }
}
