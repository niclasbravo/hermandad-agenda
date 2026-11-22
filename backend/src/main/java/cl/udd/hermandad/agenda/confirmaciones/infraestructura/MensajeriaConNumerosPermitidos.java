package cl.udd.hermandad.agenda.confirmaciones.infraestructura;

import cl.udd.hermandad.agenda.confirmaciones.dominio.IServicioMensajeria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Envía por WhatsApp real solo a los números permitidos y el resto al simulador.
 *
 * <p>Para la demo y las pruebas con el número de prueba de Meta: los pacientes ficticios de los datos demo no deben
 * recibir mensajes reales (podrían ser números de terceros) ni llenar la lista «Por llamar» con envíos fallidos.
 * Con la lista vacía todo sale por WhatsApp (comportamiento de producción). Se configura con
 * {@code WHATSAPP_NUMEROS_PERMITIDOS} (números separados por coma, con o sin +, espacios o guiones).</p>
 *
 * <p>Ambos destinos implementan {@link IServicioMensajeria}, así que NotificacionService no cambia (patrón Adapter).</p>
 */
public class MensajeriaConNumerosPermitidos implements IServicioMensajeria {

    private static final Logger log = LoggerFactory.getLogger(MensajeriaConNumerosPermitidos.class);

    private final IServicioMensajeria real;
    private final IServicioMensajeria simulador;
    private final Set<String> permitidos;

    public MensajeriaConNumerosPermitidos(IServicioMensajeria real, IServicioMensajeria simulador, Set<String> permitidos) {
        this.real = real;
        this.simulador = simulador;
        this.permitidos = permitidos.stream().map(MensajeriaConNumerosPermitidos::soloDigitos)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** Lee la lista desde el texto de configuración («+56 9 1234 5678, +56911112222»). */
    public static Set<String> desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) return Set.of();
        return java.util.Arrays.stream(texto.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    static String soloDigitos(String telefono) {
        return telefono == null ? "" : telefono.replaceAll("\\D", "");
    }

    public boolean esPermitido(String telefono) {
        return permitidos.isEmpty() || permitidos.contains(soloDigitos(telefono));
    }

    @Override
    public String enviarPlantilla(String telefono, String plantilla, List<String> parametros, List<String> botones) {
        if (esPermitido(telefono)) {
            return real.enviarPlantilla(telefono, plantilla, parametros, botones);
        }
        String d = soloDigitos(telefono);
        log.info("WhatsApp: ***{} no está en la lista de números permitidos; {} va al simulador",
                d.substring(Math.max(0, d.length() - 4)), plantilla);
        return simulador.enviarPlantilla(telefono, plantilla, parametros, botones);
    }
}
