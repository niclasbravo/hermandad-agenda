package cl.udd.hermandad.agenda.citas.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ICitaRepository {
    /** Guarda y fuerza la escritura (para detectar uq_cita_cupo_activo dentro del servicio). */
    Cita guardar(Cita cita);
    Optional<Cita> buscar(Long id);
    /** SELECT … FOR UPDATE de la cita: anular, reagendar y registrar asistencia no se pisan entre sí. */
    Optional<Cita> buscarParaModificar(Long id);
    /** Citas del día en un policlínico (incluye anuladas para el historial del día). */
    List<Cita> buscarDelDia(Integer idPoliclinico, LocalDate fecha);
    List<Cita> buscarPorPaciente(Integer idPaciente);
    /** RN-14: citas en línea activas (RESERVADA o PENDIENTE) desde hoy. */
    long contarActivasEnLinea(Integer idPaciente, LocalDate desde);
    List<Cita> pendientesDeVerificacion(Integer idPaciente);
    Optional<Cita> vigenteDeCupo(Long idCupo);
    SerieSesiones guardarSerie(SerieSesiones serie);
}
