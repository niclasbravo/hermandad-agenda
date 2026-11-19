package cl.udd.hermandad.agenda.asistencia.dominio;

import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.citas.dominio.EstadoCita;
import cl.udd.hermandad.agenda.citas.dominio.EstadoConfirmacion;

import java.time.LocalDate;
import java.util.List;

/** Objeto de valor con las citas del día de un policlínico. */
public record AgendaDelDia(LocalDate fecha, List<Cita> citas) {

    public List<Cita> vigentesOAtendidas() {
        return citas.stream().filter(c -> c.getEstado() != EstadoCita.ANULADA).toList();
    }

    public List<Cita> porLlamar() {
        return citas.stream().filter(c -> c.requiereLlamada(fecha)).toList();
    }

    public ResumenDia resumen(long enListaEspera) {
        List<Cita> activas = vigentesOAtendidas();
        long confirmadas = activas.stream().filter(c -> c.getEstadoConfirmacion() == EstadoConfirmacion.CONFIRMADA
                || c.getEstadoConfirmacion() == EstadoConfirmacion.CONFIRMADA_TELEFONO).count();
        long noEnviadas = activas.stream().filter(c -> c.estaVigente()
                && c.getEstadoConfirmacion() == EstadoConfirmacion.NO_ENVIADA).count();
        long sinRespuesta = activas.stream().filter(c -> c.estaVigente()
                && c.getEstadoConfirmacion() == EstadoConfirmacion.SIN_RESPUESTA).count();
        long pendientes = activas.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE_VERIFICACION).count();
        return new ResumenDia(activas.size(), confirmadas, noEnviadas, sinRespuesta, enListaEspera, pendientes);
    }
}
