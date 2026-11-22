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

    /**
     * Proveedor «simulador» (por defecto): todo va al simulador. Proveedor «whatsapp»: WhatsApp Cloud API; si además
     * hay números permitidos, solo esos reciben mensajes reales y el resto va al simulador (demo con datos ficticios).
     */
    @Bean
    public IServicioMensajeria servicioMensajeria(@Value("${mensajeria.proveedor:simulador}") String proveedor,
                                                  @Value("${mensajeria.whatsapp.token:}") String token,
                                                  @Value("${mensajeria.whatsapp.phone-number-id:}") String phoneNumberId,
                                                  @Value("${mensajeria.whatsapp.api-version:v25.0}") String version,
                                                  @Value("${mensajeria.whatsapp.numeros-permitidos:}") String numerosPermitidos,
                                                  @Value("${mensajeria.simulador.demora-entrega-segundos:3}") long demora,
                                                  ObjectMapper json, ObjectProvider<NotificacionService> notificaciones) {
        SimuladorMensajeriaAdapter simulador = new SimuladorMensajeriaAdapter(notificaciones, demora);
        if (!"whatsapp".equalsIgnoreCase(proveedor)) {
            return simulador;
        }
        WhatsAppBusinessAdapter whatsapp = new WhatsAppBusinessAdapter(token, phoneNumberId, version, json);
        var permitidos = MensajeriaConNumerosPermitidos.desdeTexto(numerosPermitidos);
        return permitidos.isEmpty() ? whatsapp : new MensajeriaConNumerosPermitidos(whatsapp, simulador, permitidos);
    }
}
