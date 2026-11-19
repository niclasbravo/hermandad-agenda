package cl.udd.hermandad.agenda.confirmaciones.infraestructura;

import cl.udd.hermandad.agenda.confirmaciones.dominio.IServicioMensajeria;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Adapter de WhatsApp Cloud API (Meta): POST https://graph.facebook.com/{version}/{PHONE_NUMBER_ID}/messages
 * con type=template, parámetros del cuerpo y botones de respuesta rápida.
 */
public class WhatsAppBusinessAdapter implements IServicioMensajeria {

    private final String token;
    private final String phoneNumberId;
    private final String version;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WhatsAppBusinessAdapter(String token, String phoneNumberId, String version, ObjectMapper json) {
        if (token == null || token.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalStateException("Faltan WHATSAPP_TOKEN o WHATSAPP_PHONE_NUMBER_ID");
        }
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.version = version == null || version.isBlank() ? "v21.0" : version;
        this.json = json;
    }

    @Override
    public String enviarPlantilla(String telefono, String plantilla, List<String> parametros, List<String> botones) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("messaging_product", "whatsapp");
        cuerpo.put("to", telefono.replace("+", ""));
        cuerpo.put("type", "template");
        List<Map<String, Object>> componentes = new ArrayList<>();
        componentes.add(Map.of("type", "body", "parameters",
                parametros.stream().map(p -> Map.of("type", "text", "text", p)).toList()));
        for (int i = 0; i < botones.size(); i++) {
            componentes.add(Map.of("type", "button", "sub_type", "quick_reply", "index", String.valueOf(i),
                    "parameters", List.of(Map.of("type", "payload", "payload", i == 0 ? "ASISTIRA" : "NO_ASISTIRA"))));
        }
        cuerpo.put("template", Map.of("name", plantilla, "language", Map.of("code", "es"), "components", componentes));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + version + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new ErrorEnvioMensaje("WhatsApp respondió " + res.statusCode());
            }
            JsonNode nodo = json.readTree(res.body());
            JsonNode id = nodo.path("messages").path(0).path("id");
            if (id.isMissingNode() || id.asText().isBlank()) {
                throw new ErrorEnvioMensaje("WhatsApp no devolvió el identificador del mensaje");
            }
            return id.asText();
        } catch (ErrorEnvioMensaje e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorEnvioMensaje("Envío interrumpido", e);
        } catch (Exception e) {
            throw new ErrorEnvioMensaje("No se pudo contactar a WhatsApp", e);
        }
    }
}
