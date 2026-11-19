package cl.udd.hermandad.agenda.confirmaciones.infraestructura;

import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.dominio.IServicioMensajeria;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionMensajeria {

    @Bean
    public IServicioMensajeria servicioMensajeria(@Value("${mensajeria.proveedor:simulador}") String proveedor,
                                                  @Value("${mensajeria.whatsapp.token:}") String token,
                                                  @Value("${mensajeria.whatsapp.phone-number-id:}") String phoneNumberId,
                                                  @Value("${mensajeria.whatsapp.api-version:v21.0}") String version,
                                                  @Value("${mensajeria.simulador.demora-entrega-segundos:3}") long demora,
                                                  ObjectMapper json, ObjectProvider<NotificacionService> notificaciones) {
        if ("whatsapp".equalsIgnoreCase(proveedor)) {
            return new WhatsAppBusinessAdapter(token, phoneNumberId, version, json);
        }
        return new SimuladorMensajeriaAdapter(notificaciones, demora);
    }
}
