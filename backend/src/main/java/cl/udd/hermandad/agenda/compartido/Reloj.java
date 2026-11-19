package cl.udd.hermandad.agenda.compartido;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Reloj inyectable con la zona America/Santiago. Las pruebas lo reemplazan para controlar «hoy».
 */
public class Reloj {

    public static final ZoneId ZONA = ZoneId.of("America/Santiago");

    private Clock clock;

    public Reloj(Clock clock) {
        this.clock = clock;
    }

    public static Reloj delSistema() {
        return new Reloj(Clock.system(ZONA));
    }

    public static Reloj fijo(LocalDateTime momento) {
        return new Reloj(Clock.fixed(momento.atZone(ZONA).toInstant(), ZONA));
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock).withNano(0);
    }

    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    /** Solo para pruebas: mueve el reloj a un momento fijo. */
    public void fijarEn(LocalDateTime momento) {
        this.clock = Clock.fixed(momento.atZone(ZONA).toInstant(), ZONA);
    }

    /** Solo para pruebas: vuelve al reloj del sistema. */
    public void usarSistema() {
        this.clock = Clock.system(ZONA);
    }
}
