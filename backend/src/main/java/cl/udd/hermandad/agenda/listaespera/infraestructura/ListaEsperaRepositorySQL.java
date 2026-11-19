package cl.udd.hermandad.agenda.listaespera.infraestructura;

import cl.udd.hermandad.agenda.listaespera.dominio.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ListaEsperaRepositorySQL implements IListaEsperaRepository {

    private static final String FETCH = "select i from InscripcionEspera i join fetch i.paciente pa "
            + "left join fetch pa.adultoResponsable join fetch i.policlinico pol join fetch i.profesion pr ";

    @PersistenceContext
    private EntityManager em;

    @Override
    public InscripcionEspera guardar(InscripcionEspera inscripcion) {
        if (inscripcion.getId() == null) {
            em.persist(inscripcion);
        }
        em.flush();
        return inscripcion;
    }

    @Override
    public Optional<InscripcionEspera> buscar(Integer id) {
        return em.createQuery(FETCH + "where i.id = :id", InscripcionEspera.class).setParameter("id", id)
                .getResultStream().findFirst();
    }

    @Override
    public List<InscripcionEspera> enEspera(Integer idPoliclinico, Integer idProfesion) {
        return em.createQuery(FETCH + "where i.estado = :estado and (:pol is null or pol.id = :pol) "
                        + "and (:pr is null or pr.id = :pr) order by pr.nombre, i.fechaOrden, i.id", InscripcionEspera.class)
                .setParameter("estado", EstadoInscripcion.EN_ESPERA).setParameter("pol", idPoliclinico)
                .setParameter("pr", idProfesion).getResultList();
    }

    @Override
    public List<InscripcionEspera> enEsperaDelPaciente(Integer idPaciente) {
        return em.createQuery(FETCH + "where i.estado = :estado and pa.id = :id", InscripcionEspera.class)
                .setParameter("estado", EstadoInscripcion.EN_ESPERA).setParameter("id", idPaciente).getResultList();
    }

    @Override
    public long contarEnEspera(Integer idPoliclinico) {
        return em.createQuery("select count(i) from InscripcionEspera i where i.estado = :estado "
                        + "and (:pol is null or i.policlinico.id = :pol)", Long.class)
                .setParameter("estado", EstadoInscripcion.EN_ESPERA).setParameter("pol", idPoliclinico).getSingleResult();
    }

    @Override
    public AvisoCupoLiberado guardarAviso(AvisoCupoLiberado aviso) {
        if (aviso.getId() == null) {
            em.persist(aviso);
        }
        em.flush();
        return aviso;
    }

    @Override
    public Optional<AvisoCupoLiberado> buscarAviso(Integer id) {
        return Optional.ofNullable(em.find(AvisoCupoLiberado.class, id));
    }

    @Override
    public List<AvisoCupoLiberado> avisosPendientes(Integer idPoliclinico) {
        return em.createQuery("select a from AvisoCupoLiberado a join fetch a.cupo c join fetch c.bloque b "
                        + "join fetch b.profesional p join fetch p.profesion join fetch b.box x join fetch x.policlinico pol "
                        + "where a.atendido = false and (:pol is null or pol.id = :pol) order by c.fecha, c.horaInicio",
                        AvisoCupoLiberado.class)
                .setParameter("pol", idPoliclinico).getResultList();
    }
}
