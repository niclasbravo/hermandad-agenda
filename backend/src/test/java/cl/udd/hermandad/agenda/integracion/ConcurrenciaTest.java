package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.aplicacion.ReservaEnLineaService;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** CA-RF15-3: dos reservas simultáneas del mismo cupo (una por la TENS/TONS y otra en línea) → una sola cita vigente y 409 para la otra. */
class ConcurrenciaTest extends PruebaIntegracion {

    @Autowired AgendamientoService agendamiento;
    @Autowired ReservaEnLineaService enLinea;

    private static Escenario.Base base;
    private static List<Long> cupos;
    private static int siguiente;

    @RepeatedTest(20)
    void soloUnaReservaSeConcretaYLaOtraRecibe409() throws Exception {
        if (base == null) {
            base = esc.base();
            cupos = esc.bloque(base, "08:00", "16:00");
            jdbc.update("update cupo c join bloque_horario b on b.id_bloque = c.id_bloque set c.habilitado_web = true where b.id_profesional = ?",
                    base.idProfesional());
        }
        Long cupo = cupos.get(siguiente++);
        int pacienteTens = esc.paciente(base, true);
        int pacienteWeb = esc.paciente(base, true);
        String rutWeb = texto("select numero_documento from paciente where id_paciente = ?", pacienteWeb);
        String token = enLinea.identificar(TipoDocumento.RUT, rutWeb, LocalDate.of(1970, 5, 10), "10.0.0." + siguiente).token();

        CyclicBarrier barrera = new CyclicBarrier(2);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Callable<Object> porTens = () -> {
            esc.como(base.tens());
            barrera.await();
            try {
                return agendamiento.reservar(pacienteTens, cupo).getId();
            } catch (Exception e) {
                return e;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        Callable<Object> porWeb = () -> {
            barrera.await();
            try {
                return enLinea.reservar(token, cupo).getId();
            } catch (Exception e) {
                return e;
            }
        };
        List<Future<Object>> r = new ArrayList<>(hilos.invokeAll(List.of(porTens, porWeb), 60, TimeUnit.SECONDS));
        hilos.shutdown();
        Object a = r.get(0).get();
        Object b = r.get(1).get();
        long exitos = (a instanceof Long ? 1 : 0) + (b instanceof Long ? 1 : 0);
        Object fallo = a instanceof Long ? b : a;
        assertThat(exitos).as("resultados: %s / %s", a, b).isEqualTo(1);
        assertThat(fallo).isInstanceOfSatisfying(ErrorNegocio.class, e -> {
            assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.CONFLICTO);
            assertThat(e.getMessage()).isEqualTo("Otra persona tomó este horario hace un momento. Elige otro.");
        });
        assertThat(contar("select count(*) from cita where id_cupo_activo = ?", cupo)).isEqualTo(1);
    }
}
