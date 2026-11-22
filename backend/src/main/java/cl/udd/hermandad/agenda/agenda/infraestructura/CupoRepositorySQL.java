package cl.udd.hermandad.agenda.agenda.infraestructura;

import cl.udd.hermandad.agenda.agenda.dominio.Bloqueo;
import cl.udd.hermandad.agenda.agenda.dominio.CitaAfectada;
import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.agenda.dominio.FiltroCupos;
import cl.udd.hermandad.agenda.agenda.dominio.ICupoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Repositorio de cupos (patrón Repository). Usa SQL nativo donde hay que mirar la tabla cita
 * (por la columna generada id_cupo_activo) para no depender de las clases del módulo de citas.
 */
@Repository
public class CupoRepositorySQL implements ICupoRepository {

    private static final String FETCH = "select c from Cupo c join fetch c.bloque b join fetch b.profesional p "
            + "join fetch p.profesion join fetch b.box x join fetch x.policlinico left join fetch c.bloqueo ";

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Cupo> obtenerParaReserva(Long idCupo) {
        Cupo cupo = em.find(Cupo.class, idCupo, LockModeType.PESSIMISTIC_WRITE);
        if (cupo == null) {
            return Optional.empty();
        }
        return buscar(idCupo);
    }

    @Override
    public Optional<Cupo> buscar(Long idCupo) {
        return em.createQuery(FETCH + "where c.id = :id", Cupo.class).setParameter("id", idCupo)
                .getResultStream().findFirst();
    }

    @Override
    public List<Cupo> buscarVarios(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery(FETCH + "where c.id in :ids order by c.fecha, c.horaInicio", Cupo.class)
                .setParameter("ids", ids).getResultList();
    }

    @Override
    public boolean tieneCitaVigente(Long idCupo) {
        Number n = (Number) em.createNativeQuery("select count(*) from cita where id_cupo_activo = :id")
                .setParameter("id", idCupo).getSingleResult();
        return n.longValue() > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Long> conCitaVigente(Collection<Long> idsCupo) {
        if (idsCupo.isEmpty()) {
            return Set.of();
        }
        List<Number> filas = em.createNativeQuery("select id_cupo_activo from cita where id_cupo_activo in (:ids)")
                .setParameter("ids", idsCupo).getResultList();
        Set<Long> set = new HashSet<>();
        filas.forEach(n -> set.add(n.longValue()));
        return set;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Cupo> buscarDisponibles(FiltroCupos f, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        StringBuilder sql = new StringBuilder("""
                select c.id_cupo from cupo c
                join bloque_horario b on b.id_bloque = c.id_bloque
                join box x on x.id_box = b.id_box
                join profesional p on p.id_profesional = b.id_profesional
                join profesion pr on pr.id_profesion = p.id_profesion
                where b.activo = true and p.activo = true and x.activo = true and c.id_bloqueo is null
                  and c.fecha between :desde and :hasta
                  and (c.fecha > :hoy or (c.fecha = :hoy and c.hora_inicio > :hora))
                  and c.fecha <= date_add(:hoy, interval p.plazo_publicacion_dias day)
                  and not exists (select 1 from cita ci where ci.id_cupo_activo = c.id_cupo)
                """);
        if (f.idPoliclinico() != null) sql.append(" and x.id_policlinico = :pol");
        if (f.idProfesion() != null) sql.append(" and p.id_profesion = :prof");
        if (f.idProfesional() != null) sql.append(" and p.id_profesional = :pro");
        if (f.soloWeb()) sql.append(" and c.habilitado_web = true");
        sql.append(" order by c.fecha, c.hora_inicio, p.apellidos limit 2000");
        Query q = em.createNativeQuery(sql.toString())
                .setParameter("desde", f.desde() == null ? hoy : f.desde())
                .setParameter("hasta", f.hasta() == null ? hoy.plusDays(180) : f.hasta())
                .setParameter("hoy", hoy)
                .setParameter("hora", ahora.toLocalTime());
        if (f.idPoliclinico() != null) q.setParameter("pol", f.idPoliclinico());
        if (f.idProfesion() != null) q.setParameter("prof", f.idProfesion());
        if (f.idProfesional() != null) q.setParameter("pro", f.idProfesional());
        List<Number> ids = q.getResultList();
        return buscarVarios(ids.stream().map(Number::longValue).toList());
    }

    @Override
    public List<Cupo> guardarTodos(List<Cupo> cupos) {
        for (Cupo c : cupos) {
            if (c.getId() == null) {
                em.persist(c);
            }
        }
        em.flush();
        return cupos;
    }

    @Override
    public Cupo guardar(Cupo cupo) {
        if (cupo.getId() == null) {
            em.persist(cupo);
        }
        em.flush();
        return cupo;
    }

    @Override
    public Bloqueo guardarBloqueo(Bloqueo bloqueo) {
        em.persist(bloqueo);
        em.flush();
        return bloqueo;
    }

    @Override
    public List<Cupo> delBloque(Integer idBloque, LocalDate desde, LocalDate hasta) {
        return em.createQuery(FETCH + "where b.id = :id and c.fecha between :desde and :hasta order by c.fecha, c.horaInicio", Cupo.class)
                .setParameter("id", idBloque)
                .setParameter("desde", desde == null ? LocalDate.of(2000, 1, 1) : desde)
                .setParameter("hasta", hasta == null ? LocalDate.of(2999, 12, 31) : hasta)
                .getResultList();
    }

    @Override
    public List<Cupo> deLaSemana(Integer idProfesional, Integer idPoliclinico, Integer idBox, LocalDate desde, LocalDate hasta,
                                LocalDate hoy) {
        // Fechas pasadas: se muestran siempre (historial, aunque el bloque ya no esté activo). Desde hoy: solo los cupos
        // de bloques activos y dentro de su vigencia (los de un bloque cerrado al modificarlo no se duplican, D-10).
        StringBuilder jpql = new StringBuilder(FETCH + "where c.fecha between :desde and :hasta "
                + "and (c.fecha < :hoy or (b.activo = true and c.fecha between b.vigenciaDesde and b.vigenciaHasta))");
        if (idProfesional != null) jpql.append(" and p.id = :pro");
        if (idPoliclinico != null) jpql.append(" and x.policlinico.id = :pol");
        if (idBox != null) jpql.append(" and x.id = :box");
        jpql.append(" order by c.fecha, c.horaInicio");
        var q = em.createQuery(jpql.toString(), Cupo.class).setParameter("desde", desde).setParameter("hasta", hasta)
                .setParameter("hoy", hoy);
        if (idProfesional != null) q.setParameter("pro", idProfesional);
        if (idPoliclinico != null) q.setParameter("pol", idPoliclinico);
        if (idBox != null) q.setParameter("box", idBox);
        return q.getResultList();
    }

    @Override
    public Optional<Cupo> buscarPorProfesionalFechaHora(Integer idProfesional, LocalDate fecha, LocalTime hora) {
        // Puede haber dos cupos a la misma hora si el bloque se modificó: se prefiere el que está vigente y sin bloquear.
        return em.createQuery(FETCH + "where b.activo = true and p.id = :pro and c.fecha = :fecha and c.horaInicio = :hora "
                        + "and c.fecha between b.vigenciaDesde and b.vigenciaHasta "
                        + "order by case when c.bloqueo is null then 0 else 1 end, c.id desc", Cupo.class)
                .setParameter("pro", idProfesional).setParameter("fecha", fecha).setParameter("hora", hora)
                .setMaxResults(1).getResultStream().findFirst();
    }

    @Override
    public List<Cupo> futurosDeBloquesActivos(LocalDate desde) {
        return em.createQuery(FETCH + "where b.activo = true and c.fecha >= :desde order by b.id, c.fecha, c.horaInicio", Cupo.class)
                .setParameter("desde", desde).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CitaAfectada> citasVigentes(Collection<Long> idsCupo) {
        if (idsCupo.isEmpty()) {
            return List.of();
        }
        List<Object[]> filas = em.createNativeQuery("""
                select ci.id_cita, c.id_cupo, concat(pa.nombres, ' ', pa.apellidos), pa.numero_documento,
                       pa.telefono_celular, c.fecha, c.hora_inicio
                from cita ci join cupo c on c.id_cupo = ci.id_cupo join paciente pa on pa.id_paciente = ci.id_paciente
                where ci.id_cupo_activo in (:ids) order by c.fecha, c.hora_inicio""")
                .setParameter("ids", idsCupo).getResultList();
        return filas.stream().map(f -> new CitaAfectada(((Number) f[0]).longValue(), ((Number) f[1]).longValue(),
                (String) f[2], (String) f[3], (String) f[4], aFecha(f[5]), aHora(f[6]))).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, String> pacientesDeCupos(Collection<Long> idsCupo) {
        if (idsCupo.isEmpty()) {
            return Map.of();
        }
        List<Object[]> filas = em.createNativeQuery("""
                select ci.id_cupo_activo, concat(pa.nombres, ' ', pa.apellidos)
                from cita ci join paciente pa on pa.id_paciente = ci.id_paciente
                where ci.id_cupo_activo in (:ids)""").setParameter("ids", idsCupo).getResultList();
        Map<Long, String> mapa = new HashMap<>();
        filas.forEach(f -> mapa.put(((Number) f[0]).longValue(), (String) f[1]));
        return mapa;
    }

    static LocalDate aFecha(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
    }

    static LocalTime aHora(Object o) {
        if (o instanceof LocalTime t) return t;
        if (o instanceof java.sql.Time t) return t.toLocalTime();
        return LocalTime.parse(o.toString());
    }
}
