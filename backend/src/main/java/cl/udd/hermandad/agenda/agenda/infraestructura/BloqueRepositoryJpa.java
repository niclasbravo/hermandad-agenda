package cl.udd.hermandad.agenda.agenda.infraestructura;

import cl.udd.hermandad.agenda.agenda.dominio.BloqueHorario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BloqueRepositoryJpa extends Repository<BloqueHorario, Integer> {

    BloqueHorario saveAndFlush(BloqueHorario bloque);

    @Query("select b from BloqueHorario b join fetch b.profesional p join fetch p.profesion join fetch b.box x join fetch x.policlinico where b.id = :id")
    Optional<BloqueHorario> buscar(@Param("id") Integer id);

    @Query("""
            select b from BloqueHorario b join fetch b.profesional p join fetch b.box x join fetch x.policlinico
            where b.activo = true and b.diaSemana = :dia and (:id is null or b.id <> :id)
              and (p.id = :idProfesional or x.id = :idBox)
              and b.horaInicio < :termino and :inicio < b.horaTermino
              and b.vigenciaDesde <= :hasta and :desde <= b.vigenciaHasta""")
    List<BloqueHorario> superpuestos(@Param("id") Integer id, @Param("dia") Integer dia,
                                     @Param("idProfesional") Integer idProfesional, @Param("idBox") Integer idBox,
                                     @Param("inicio") LocalTime inicio, @Param("termino") LocalTime termino,
                                     @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query("select b from BloqueHorario b join fetch b.profesional p join fetch p.profesion join fetch b.box x join fetch x.policlinico where b.activo = true and p.id = :idProfesional order by b.diaSemana, b.horaInicio")
    List<BloqueHorario> activosDelProfesional(@Param("idProfesional") Integer idProfesional);

    @Query("select b from BloqueHorario b join fetch b.profesional p join fetch p.profesion join fetch b.box x join fetch x.policlinico where b.activo = true order by b.id")
    List<BloqueHorario> activos();
}
