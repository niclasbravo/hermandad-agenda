package cl.udd.hermandad.agenda.confirmaciones.infraestructura;

import cl.udd.hermandad.agenda.confirmaciones.dominio.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MensajeRepositorySQL implements IMensajeRepository {

    private static final String FETCH = "select m from Mensaje m join fetch m.plantilla join fetch m.cita ci "
            + "join fetch ci.paciente pa left join fetch pa.adultoResponsable join fetch ci.cupo c join fetch c.bloque b "
            + "join fetch b.profesional p join fetch p.profesion join fetch b.box x join fetch x.policlinico ";

    @PersistenceContext
    private EntityManager em;

    @Override
    public Mensaje guardar(Mensaje mensaje) {
        if (mensaje.getId() == null) {
            em.persist(mensaje);
        }
        em.flush();
        return mensaje;
    }

    @Override
    public Optional<Mensaje> buscar(Long id) {
        return em.createQuery(FETCH + "where m.id = :id", Mensaje.class).setParameter("id", id).getResultStream().findFirst();
    }

    @Override
    public Optional<Mensaje> buscarPorIdExterno(String idExterno) {
        return em.createQuery(FETCH + "where m.idExterno = :id", Mensaje.class).setParameter("id", idExterno)
                .getResultStream().findFirst();
    }

    @Override
    public List<Long> pendientes(LocalDateTime ahora, int limite) {
        return em.createQuery("select m.id from Mensaje m where m.estadoEntrega = :estado and m.fechaProgramada <= :ahora "
                        + "order by m.fechaProgramada, m.id", Long.class)
                .setParameter("estado", EstadoEntrega.PROGRAMADO).setParameter("ahora", ahora)
                .setMaxResults(limite).getResultList();
    }

    @Override
    public List<Mensaje> deCita(Long idCita) {
        return em.createQuery(FETCH + "where ci.id = :id order by m.fechaProgramada, m.id", Mensaje.class)
                .setParameter("id", idCita).getResultList();
    }

    @Override
    public List<Mensaje> programadosDeCita(Long idCita) {
        return em.createQuery(FETCH + "where ci.id = :id and m.estadoEntrega = :estado", Mensaje.class)
                .setParameter("id", idCita).setParameter("estado", EstadoEntrega.PROGRAMADO).getResultList();
    }

    @Override
    public List<Mensaje> enviadosA(String telefono, int limite) {
        return em.createQuery(FETCH + "where m.telefonoDestino = :tel and m.estadoEntrega <> :prog "
                        + "order by coalesce(m.fechaEnvio, m.fechaProgramada) desc, m.id desc", Mensaje.class)
                .setParameter("tel", telefono).setParameter("prog", EstadoEntrega.PROGRAMADO)
                .setMaxResults(limite).getResultList();
    }

    @Override
    public List<String> telefonosConMensajes() {
        return em.createQuery("select m.telefonoDestino from Mensaje m where m.estadoEntrega <> :prog "
                        + "group by m.telefonoDestino order by max(m.id) desc", String.class)
                .setParameter("prog", EstadoEntrega.PROGRAMADO).setMaxResults(100).getResultList();
    }

    @Override
    public RespuestaPaciente guardarRespuesta(RespuestaPaciente respuesta) {
        em.persist(respuesta);
        em.flush();
        return respuesta;
    }

    @Override
    public List<RespuestaPaciente> respuestasDeCita(Long idCita) {
        return em.createQuery("select r from RespuestaPaciente r join fetch r.mensaje m where m.cita.id = :id "
                + "order by r.fechaHora, r.id", RespuestaPaciente.class).setParameter("id", idCita).getResultList();
    }

    @Override
    public LlamadaConfirmacion guardarLlamada(LlamadaConfirmacion llamada) {
        em.persist(llamada);
        em.flush();
        return llamada;
    }

    @Override
    public List<LlamadaConfirmacion> llamadasDeCita(Long idCita) {
        return em.createQuery("select l from LlamadaConfirmacion l where l.cita.id = :id order by l.fechaHora, l.id",
                LlamadaConfirmacion.class).setParameter("id", idCita).getResultList();
    }

    @Override
    public Optional<PlantillaMensaje> plantilla(String nombreProveedor) {
        return em.createQuery("select p from PlantillaMensaje p where p.nombreProveedor = :n", PlantillaMensaje.class)
                .setParameter("n", nombreProveedor).getResultStream().findFirst();
    }

    @Override
    public PlantillaMensaje guardarPlantilla(PlantillaMensaje plantilla) {
        em.persist(plantilla);
        em.flush();
        return plantilla;
    }
}
