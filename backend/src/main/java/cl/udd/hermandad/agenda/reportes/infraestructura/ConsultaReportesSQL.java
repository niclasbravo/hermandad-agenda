package cl.udd.hermandad.agenda.reportes.infraestructura;

import cl.udd.hermandad.agenda.pacientes.dominio.DocumentoIdentidad;
import cl.udd.hermandad.agenda.pacientes.dominio.Telefono;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import cl.udd.hermandad.agenda.reportes.dominio.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

/** Implementación SQL de los reportes (consultas en vivo, sin tablas propias). */
@Repository
public class ConsultaReportesSQL implements IConsultaReportes {

    private static final String JOINS = """
             join cupo c on c.id_cupo = ci.id_cupo
             join bloque_horario b on b.id_bloque = c.id_bloque
             join box x on x.id_box = b.id_box
             join profesional p on p.id_profesional = b.id_profesional
             join profesion pr on pr.id_profesion = p.id_profesion
            """;

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<ReporteOcupacion.Fila> ocupacion(FiltroReporte f, LocalDate hoy) {
        String sql = """
                select x.id_policlinico, pol.nombre, p.id_profesional, concat(p.nombres, ' ', p.apellidos), pr.nombre,
                       count(*), count(ci.id_cita)
                from cupo c
                join bloque_horario b on b.id_bloque = c.id_bloque
                join box x on x.id_box = b.id_box
                join policlinico pol on pol.id_policlinico = x.id_policlinico
                join profesional p on p.id_profesional = b.id_profesional
                join profesion pr on pr.id_profesion = p.id_profesion
                left join cita ci on ci.id_cupo_activo = c.id_cupo
                where c.fecha between :desde and :hasta and c.id_bloqueo is null
                  and c.fecha <= date_add(:hoy, interval p.plazo_publicacion_dias day)
                """ + filtros(f, "x.id_policlinico", "p.id_profesional") + "\n" + """
                 group by x.id_policlinico, pol.nombre, p.id_profesional, p.nombres, p.apellidos, pr.nombre
                 order by pol.nombre, p.apellidos""";
        Query q = parametros(em.createNativeQuery(sql), f).setParameter("hoy", hoy);
        List<Object[]> filas = q.getResultList();
        return filas.stream().map(r -> new ReporteOcupacion.Fila(num(r[0]).intValue(), (String) r[1], num(r[2]).intValue(),
                (String) r[3], (String) r[4], num(r[5]).longValue(), num(r[6]).longValue())).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReporteAsistencia.Conteo> asistenciaPorProfesion(FiltroReporte f) {
        String sql = "select pr.nombre, count(*), sum(ci.estado = 'ASISTIDA'), sum(ci.estado = 'INASISTIDA') from cita ci "
                + JOINS + " where ci.estado <> 'ANULADA' and c.fecha between :desde and :hasta "
                + filtros(f, "x.id_policlinico", "p.id_profesional") + " group by pr.nombre order by pr.nombre";
        List<Object[]> filas = parametros(em.createNativeQuery(sql), f).getResultList();
        return filas.stream().map(r -> new ReporteAsistencia.Conteo((String) r[0], num(r[1]).longValue(),
                num(r[2]).longValue(), num(r[3]).longValue())).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReporteAsistencia.Conteo> asistenciaPorMes(FiltroReporte f) {
        String sql = "select date_format(c.fecha, '%Y-%m'), count(*), sum(ci.estado = 'ASISTIDA'), sum(ci.estado = 'INASISTIDA') "
                + "from cita ci " + JOINS + " where ci.estado <> 'ANULADA' and c.fecha between :desde and :hasta "
                + filtros(f, "x.id_policlinico", "p.id_profesional") + " group by date_format(c.fecha, '%Y-%m') order by 1";
        List<Object[]> filas = parametros(em.createNativeQuery(sql), f).getResultList();
        return filas.stream().map(r -> new ReporteAsistencia.Conteo((String) r[0], num(r[1]).longValue(),
                num(r[2]).longValue(), num(r[3]).longValue())).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReporteDemanda.Fila> demanda(FiltroReporte f) {
        String sql = """
                select p.id_profesional, concat(p.nombres, ' ', p.apellidos), pr.nombre,
                  (select group_concat(pol.nombre order by pol.nombre separator ', ') from profesional_policlinico pp
                     join policlinico pol on pol.id_policlinico = pp.id_policlinico
                     where pp.id_profesional = p.id_profesional and (:pol is null or pp.id_policlinico = :pol)),
                  (select count(*) from cita ci join cupo c on c.id_cupo = ci.id_cupo
                     join bloque_horario b on b.id_bloque = c.id_bloque join box x on x.id_box = b.id_box
                     where b.id_profesional = p.id_profesional and ci.estado <> 'ANULADA'
                       and c.fecha between :desde and :hasta and (:pol is null or x.id_policlinico = :pol)),
                  (select count(*) from inscripcion_espera ie
                     join profesional_policlinico pp on pp.id_policlinico = ie.id_policlinico and pp.id_profesional = p.id_profesional
                     where ie.estado = 'EN_ESPERA' and ie.id_profesion = p.id_profesion
                       and (:pol is null or ie.id_policlinico = :pol))
                from profesional p join profesion pr on pr.id_profesion = p.id_profesion
                where p.activo = true and (:pro is null or p.id_profesional = :pro)
                  and (:pol is null or exists (select 1 from profesional_policlinico pp2
                        where pp2.id_profesional = p.id_profesional and pp2.id_policlinico = :pol))
                order by pr.nombre, p.apellidos""";
        Query q = em.createNativeQuery(sql).setParameter("desde", f.desde()).setParameter("hasta", f.hasta())
                .setParameter("pol", f.idPoliclinico()).setParameter("pro", f.idProfesional());
        List<Object[]> filas = q.getResultList();
        return filas.stream().map(r -> new ReporteDemanda.Fila(num(r[0]).intValue(), (String) r[1], (String) r[2],
                r[3] == null ? "" : r[3].toString(), num(r[4]).longValue(), num(r[5]).longValue())).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PacienteAtendido> pacientesAtendidos(FiltroReporte f) {
        String sql = "select distinct pa.id_paciente, pa.fecha_nacimiento, pa.genero, pa.nacionalidad from cita ci " + JOINS
                + " join paciente pa on pa.id_paciente = ci.id_paciente where ci.estado = 'ASISTIDA' "
                + "and c.fecha between :desde and :hasta " + filtros(f, "x.id_policlinico", "p.id_profesional");
        List<Object[]> filas = parametros(em.createNativeQuery(sql), f).getResultList();
        return filas.stream().map(r -> new PacienteAtendido(num(r[0]).intValue(), fecha(r[1]), (String) r[2], (String) r[3])).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ListadoInasistencia.Fila> inasistencias(FiltroReporte f) {
        String sql = """
                select pa.id_paciente, concat(pa.nombres, ' ', pa.apellidos), pa.tipo_documento, pa.numero_documento,
                       count(*), max(c.fecha), pa.telefono_celular,
                       (select pr2.nombre from cita ci2 join cupo c2 on c2.id_cupo = ci2.id_cupo
                          join bloque_horario b2 on b2.id_bloque = c2.id_bloque join box x2 on x2.id_box = b2.id_box
                          join profesional p2 on p2.id_profesional = b2.id_profesional
                          join profesion pr2 on pr2.id_profesion = p2.id_profesion
                         where ci2.id_paciente = pa.id_paciente and ci2.estado = 'INASISTIDA'
                           and c2.fecha between :desde and :hasta and (:pol is null or x2.id_policlinico = :pol)
                         order by c2.fecha desc, c2.hora_inicio desc limit 1)
                from cita ci
                """ + JOINS + """
                 join paciente pa on pa.id_paciente = ci.id_paciente
                 where ci.estado = 'INASISTIDA' and c.fecha between :desde and :hasta
                   and (:pol is null or x.id_policlinico = :pol) and (:pro is null or p.id_profesional = :pro)
                 group by pa.id_paciente, pa.nombres, pa.apellidos, pa.tipo_documento, pa.numero_documento, pa.telefono_celular
                 order by count(*) desc, max(c.fecha) desc""";
        Query q = em.createNativeQuery(sql).setParameter("desde", f.desde()).setParameter("hasta", f.hasta())
                .setParameter("pol", f.idPoliclinico()).setParameter("pro", f.idProfesional());
        List<Object[]> filas = q.getResultList();
        return filas.stream().map(r -> new ListadoInasistencia.Fila(num(r[0]).intValue(), (String) r[1],
                DocumentoIdentidad.de(TipoDocumento.valueOf((String) r[2]), (String) r[3]).formatear(),
                num(r[4]).longValue(), fecha(r[5]), (String) r[7], Telefono.formatear((String) r[6]))).toList();
    }

    @Override
    public List<CategoriaEtaria> categorias() {
        return em.createQuery("select c from CategoriaEtaria c order by c.edadDesde", CategoriaEtaria.class).getResultList();
    }

    @Override
    public CategoriaEtaria guardarCategoria(CategoriaEtaria c) {
        if (c.getId() == null) {
            em.persist(c);
        }
        em.flush();
        return c;
    }

    @Override
    public void eliminarCategoria(CategoriaEtaria c) {
        em.remove(c);
        em.flush();
    }

    private static String filtros(FiltroReporte f, String colPol, String colPro) {
        return (f.idPoliclinico() != null ? " and " + colPol + " = :pol" : "")
                + (f.idProfesional() != null ? " and " + colPro + " = :pro" : "");
    }

    private static Query parametros(Query q, FiltroReporte f) {
        q.setParameter("desde", f.desde()).setParameter("hasta", f.hasta());
        if (f.idPoliclinico() != null) q.setParameter("pol", f.idPoliclinico());
        if (f.idProfesional() != null) q.setParameter("pro", f.idProfesional());
        return q;
    }

    private static Number num(Object o) {
        return o == null ? 0 : (Number) o;
    }

    private static LocalDate fecha(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
    }
}
