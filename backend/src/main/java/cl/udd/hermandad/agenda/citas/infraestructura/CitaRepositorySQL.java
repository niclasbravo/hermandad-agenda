package cl.udd.hermandad.agenda.citas.infraestructura;

import cl.udd.hermandad.agenda.citas.dominio.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Implementación SQL del repositorio de citas (patrón Repository; AgendamientoService solo conoce la interfaz). */
@Repository
public class CitaRepositorySQL implements ICitaRepository {

    static final String FETCH = "select ci from Cita ci join fetch ci.paciente pa left join fetch pa.adultoResponsable "
            + "join fetch ci.cupo c join fetch c.bloque b join fetch b.profesional p join fetch p.profesion "
            + "join fetch b.box x join fetch x.policlinico ";

    @PersistenceContext
    private EntityManager em;

    @Override
    public Cita guardar(Cita cita) {
        if (cita.getId() == null) {
            em.persist(cita);
        }
        em.flush();
        if (cita.getId() != null) {
            // El disparador puede cambiar el estado (RN-13): se relee desde la base.
            em.refresh(cita);
        }
        return cita;
    }

    @Override
    public Optional<Cita> buscar(Long id) {
        return em.createQuery(FETCH + "where ci.id = :id", Cita.class).setParameter("id", id).getResultStream().findFirst();
    }

    @Override
    public List<Cita> buscarDelDia(Integer idPoliclinico, LocalDate fecha) {
        return em.createQuery(FETCH + "where c.fecha = :fecha and (:pol is null or x.policlinico.id = :pol) "
                        + "order by c.horaInicio, p.apellidos", Cita.class)
                .setParameter("fecha", fecha).setParameter("pol", idPoliclinico).getResultList();
    }

    @Override
    public List<Cita> buscarPorPaciente(Integer idPaciente) {
        return em.createQuery(FETCH + "where pa.id = :id order by c.fecha desc, c.horaInicio desc", Cita.class)
                .setParameter("id", idPaciente).getResultList();
    }

    @Override
    public long contarActivasEnLinea(Integer idPaciente, LocalDate desde) {
        return em.createQuery("select count(ci) from Cita ci where ci.paciente.id = :id and ci.canalReserva = :canal "
                        + "and ci.estado in :estados and ci.cupo.fecha >= :desde", Long.class)
                .setParameter("id", idPaciente).setParameter("canal", CanalReserva.EN_LINEA)
                .setParameter("estados", List.of(EstadoCita.RESERVADA, EstadoCita.PENDIENTE_VERIFICACION))
                .setParameter("desde", desde).getSingleResult();
    }

    @Override
    public List<Cita> pendientesDeVerificacion(Integer idPaciente) {
        return em.createQuery(FETCH + "where pa.id = :id and ci.estado = :estado order by c.fecha", Cita.class)
                .setParameter("id", idPaciente).setParameter("estado", EstadoCita.PENDIENTE_VERIFICACION).getResultList();
    }

    @Override
    public Optional<Cita> vigenteDeCupo(Long idCupo) {
        return em.createQuery(FETCH + "where c.id = :id and ci.estado <> :anulada", Cita.class)
                .setParameter("id", idCupo).setParameter("anulada", EstadoCita.ANULADA).getResultStream().findFirst();
    }

    @Override
    public SerieSesiones guardarSerie(SerieSesiones serie) {
        em.persist(serie);
        em.flush();
        return serie;
    }
}
