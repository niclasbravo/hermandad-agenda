package cl.udd.hermandad.agenda.agenda.infraestructura;

import cl.udd.hermandad.agenda.agenda.dominio.BloqueHorario;
import cl.udd.hermandad.agenda.agenda.dominio.IBloqueRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BloqueRepositorySQL implements IBloqueRepository {

    private final BloqueRepositoryJpa jpa;

    public BloqueRepositorySQL(BloqueRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public BloqueHorario guardar(BloqueHorario bloque) {
        return jpa.saveAndFlush(bloque);
    }

    @Override
    public Optional<BloqueHorario> buscar(Integer id) {
        return jpa.buscar(id);
    }

    @Override
    public List<BloqueHorario> buscarSuperpuestos(BloqueHorario b) {
        return jpa.superpuestos(b.getId(), b.getDiaSemana(), b.getProfesional().getId(), b.getBox().getId(),
                b.getHoraInicio(), b.getHoraTermino(), b.getVigenciaDesde(), b.getVigenciaHasta());
    }

    @Override
    public List<BloqueHorario> activosDelProfesional(Integer idProfesional) {
        return jpa.activosDelProfesional(idProfesional);
    }

    @Override
    public List<BloqueHorario> activos() {
        return jpa.activos();
    }
}
