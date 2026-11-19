package cl.udd.hermandad.agenda.confirmaciones.api;

import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.confirmaciones.dominio.EstadoEntrega;
import cl.udd.hermandad.agenda.confirmaciones.dominio.Respuesta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Webhook de WhatsApp Cloud API (sección 10.5). Verifica la firma antes de procesar y responde 200 rápido. */
@RestController
@RequestMapping("/webhooks/whatsapp")
public class WebhookWhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WebhookWhatsAppController.class);

    private final NotificacionService notificaciones;
    private final ObjectMapper json;
    private final String appSecret;
    private final String verifyToken;

    public WebhookWhatsAppController(NotificacionService notificaciones, ObjectMapper json,
                                     @Value("${mensajeria.whatsapp.app-secret:}") String appSecret,
                                     @Value("${mensajeria.whatsapp.verify-token:}") String verifyToken) {
        this.notificaciones = notificaciones;
        this.json = json;
        this.appSecret = appSecret;
        this.verifyToken = verifyToken;
    }

    /** Verificación de Meta: hub.mode, hub.verify_token y hub.challenge. */
    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode", required = false) String modo,
                                            @RequestParam(name = "hub.verify_token", required = false) String token,
                                            @RequestParam(name = "hub.challenge", required = false) String desafio) {
        if ("subscribe".equals(modo) && !verifyToken.isBlank() && iguales(verifyToken, token)) {
            return ResponseEntity.ok(desafio);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> recibir(@RequestHeader(name = "X-Hub-Signature-256", required = false) String firma,
                                        @RequestBody(required = false) String cuerpo) {
        if (cuerpo == null || !firmaValida(cuerpo, firma)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            procesar(json.readTree(cuerpo));
        } catch (Exception e) {
            log.warn("Webhook de WhatsApp con contenido no procesable: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    void procesar(JsonNode raiz) {
        for (JsonNode entrada : raiz.path("entry")) {
            for (JsonNode cambio : entrada.path("changes")) {
                JsonNode valor = cambio.path("value");
                for (JsonNode st : valor.path("statuses")) {
                    EstadoEntrega estado = switch (st.path("status").asText()) {
                        case "sent" -> EstadoEntrega.ENVIADO;
                        case "delivered" -> EstadoEntrega.ENTREGADO;
                        case "read" -> EstadoEntrega.LEIDO;
                        case "failed" -> EstadoEntrega.FALLIDO;
                        default -> null;
                    };
                    if (estado != null) {
                        notificaciones.procesarEstado(st.path("id").asText(), estado);
                    }
                }
                for (JsonNode m : valor.path("messages")) {
                    String original = m.path("context").path("id").asText(null);
                    String carga = m.path("button").path("payload").asText(null);
                    if (carga == null) {
                        carga = m.path("interactive").path("button_reply").path("id").asText(null);
                    }
                    String texto = m.path("button").path("text").asText("");
                    Respuesta r = null;
                    if ("ASISTIRA".equals(carga) || texto.startsWith("Asistir")) {
                        r = Respuesta.ASISTIRA;
                    } else if ("NO_ASISTIRA".equals(carga) || texto.startsWith("No podr")) {
                        r = Respuesta.NO_ASISTIRA;
                    }
                    if (original != null && r != null) {
                        notificaciones.procesarRespuesta(original, r, m.path("id").asText(null));
                    }
                }
            }
        }
    }

    boolean firmaValida(String cuerpo, String firma) {
        if (appSecret.isBlank() || firma == null || !firma.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperada = "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
            return iguales(esperada, firma);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean iguales(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
