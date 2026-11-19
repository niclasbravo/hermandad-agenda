package cl.udd.hermandad.agenda.pacientes.infraestructura;

import cl.udd.hermandad.agenda.pacientes.dominio.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PacienteRepositorySQL implements IPacienteRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Paciente guardar(Paciente paciente) {
        if (paciente.getId() == null) {
            em.persist(paciente);
        }
        em.flush();
        return paciente;
    }

    @Override
    public Optional<Paciente> buscar(Integer id) {
        return Optional.ofNullable(em.find(Paciente.class, id));
    }

    @Override
    public Optional<Paciente> buscarPorDocumento(TipoDocumento tipo, String numero) {
        return em.createQuery("select p from Paciente p where p.documento.tipo = :t and p.documento.numero = :n", Paciente.class)
                .setParameter("t", tipo).setParameter("n", numero).getResultStream().findFirst();
    }

    @Override
    public List<Paciente> buscarPorNumero(String numero) {
        return em.createQuery("select p from Paciente p where p.documento.numero = :n order by p.id", Paciente.class)
                .setParameter("n", numero).getResultList();
    }

    @Override
    public Optional<AdultoResponsable> buscarAdulto(TipoDocumento tipo, String numero) {
        return em.createQuery("select a from AdultoResponsable a where a.documento.tipo = :t and a.documento.numero = :n",
                        AdultoResponsable.class)
                .setParameter("t", tipo).setParameter("n", numero).getResultStream().findFirst();
    }

    @Override
    public AdultoResponsable guardarAdulto(AdultoResponsable adulto) {
        if (adulto.getId() == null) {
            em.persist(adulto);
        }
        em.flush();
        return adulto;
    }

    @Override
    public VerificacionRequisitos guardarVerificacion(VerificacionRequisitos verificacion) {
        em.persist(verificacion);
        em.flush();
        return verificacion;
    }

    @Override
    public DocumentoAdjunto guardarDocumento(DocumentoAdjunto documento) {
        if (documento.getId() == null) {
            em.persist(documento);
        }
        em.flush();
        return documento;
    }

    @Override
    public Optional<DocumentoAdjunto> buscarDocumento(Integer id) {
        return Optional.ofNullable(em.find(DocumentoAdjunto.class, id));
    }

    @Override
    public List<DocumentoAdjunto> documentosDe(Integer idPaciente) {
        return em.createQuery("select d from DocumentoAdjunto d where d.paciente.id = :id order by d.fechaCarga, d.id",
                DocumentoAdjunto.class).setParameter("id", idPaciente).getResultList();
    }

    @Override
    public long contar() {
        return em.createQuery("select count(p) from Paciente p", Long.class).getSingleResult();
    }
}
