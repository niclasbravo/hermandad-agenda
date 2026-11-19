package cl.udd.hermandad.agenda.agenda.dominio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ICupoRepository {
    /** SELECT … FOR UPDATE del cupo a reservar (RN-02). */
    Optional<Cupo> obtenerParaReserva(Long idCupo);
    Optional<Cupo> buscar(Long idCupo);
    List<Cupo> buscarVarios(Collection<Long> ids);
    boolean tieneCitaVigente(Long idCupo);
    Set<Long> conCitaVigente(Collection<Long> idsCupo);
    /** Cupos DISPONIBLES (publicados, no bloqueados, sin reserva vigente, futuros, de bloques activos). */
    List<Cupo> buscarDisponibles(FiltroCupos filtro, LocalDateTime ahora);
    /** Solo los disponibles y habilitado_web. */
    default List<Cupo> buscarDisponiblesWeb(FiltroCupos filtro, LocalDateTime ahora) {
        return buscarDisponibles(new FiltroCupos(filtro.idPoliclinico(), filtro.idProfesion(), filtro.idProfesional(),
                filtro.desde(), filtro.hasta(), true), ahora);
    }
    List<Cupo> guardarTodos(List<Cupo> cupos);
    Cupo guardar(Cupo cupo);
    Bloqueo guardarBloqueo(Bloqueo bloqueo);
    List<Cupo> delBloque(Integer idBloque, LocalDate desde, LocalDate hasta);
    /** Cupos de bloques activos para la vista semanal; los filtros nulos no se aplican. */
    List<Cupo> deLaSemana(Integer idProfesional, Integer idPoliclinico, Integer idBox, LocalDate desde, LocalDate hasta);
    Optional<Cupo> buscarPorProfesionalFechaHora(Integer idProfesional, LocalDate fecha, LocalTime hora);
    List<Cupo> futurosDeBloquesActivos(LocalDate desde);
    /** Citas vigentes de esos cupos, con los datos mínimos para mostrarlas (D-10). */
    List<CitaAfectada> citasVigentes(Collection<Long> idsCupo);
    /** Nombre del paciente de la cita vigente de cada cupo (vista semanal). */
    java.util.Map<Long, String> pacientesDeCupos(Collection<Long> idsCupo);
}
