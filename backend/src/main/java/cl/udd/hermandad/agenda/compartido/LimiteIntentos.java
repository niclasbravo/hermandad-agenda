package cl.udd.hermandad.agenda.compartido;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/** Límite de intentos fallidos de identificación en línea: 5 por IP cada 10 minutos (respuesta 429 genérica). */
@Component
public class LimiteIntentos {

    public static final int MAXIMO = 5;
    public static final Duration VENTANA = Duration.ofMinutes(10);

    private final Cache<String, Deque<Instant>> fallidos = Caffeine.newBuilder()
            .expireAfterAccess(VENTANA).maximumSize(50_000).build();

    public void verificar(String ip) {
        Deque<Instant> lista = fallidos.getIfPresent(ip);
        if (lista == null) {
            return;
        }
        synchronized (lista) {
            limpiar(lista);
            if (lista.size() >= MAXIMO) {
                throw new ErrorNegocio(ErrorNegocio.Tipo.DEMASIADOS_INTENTOS, "DEMASIADOS_INTENTOS",
                        "Hiciste muchos intentos. Espera unos minutos e inténtalo de nuevo, o pide tu hora en tu policlínico.",
                        Map.of());
            }
        }
    }

    public void registrarFallido(String ip) {
        Deque<Instant> lista = fallidos.get(ip, k -> new ArrayDeque<>());
        synchronized (lista) {
            limpiar(lista);
            lista.addLast(Instant.now());
        }
    }

    public void reiniciar() {
        fallidos.invalidateAll();
    }

    private static void limpiar(Deque<Instant> lista) {
        Instant limite = Instant.now().minus(VENTANA);
        while (!lista.isEmpty() && lista.peekFirst().isBefore(limite)) {
            lista.pollFirst();
        }
    }
}
