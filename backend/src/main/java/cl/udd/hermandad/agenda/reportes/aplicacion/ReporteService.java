package cl.udd.hermandad.agenda.reportes.aplicacion;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.reportes.dominio.*;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** CU-07. Reportes calculados en vivo (RNF-12). */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    private final IConsultaReportes consultas;
    private final ParametroService parametros;
    private final Reloj reloj;

    public ReporteService(IConsultaReportes consultas, ParametroService parametros, Reloj reloj) {
        this.consultas = consultas;
        this.parametros = parametros;
        this.reloj = reloj;
    }

    public ReporteOcupacion ocupacion(FiltroReporte f) {
        LocalDate hoy = reloj.hoy();
        List<ReporteOcupacion.Fila> anterior = consultas.ocupacion(f.anterior(), hoy);
        return new ReporteOcupacion(f, consultas.ocupacion(f, hoy),
                anterior.stream().mapToLong(ReporteOcupacion.Fila::ofrecidos).sum(),
                anterior.stream().mapToLong(ReporteOcupacion.Fila::reservados).sum());
    }

    public ReporteAsistencia asistencia(FiltroReporte f) {
        List<ReporteAsistencia.Conteo> porProfesion = consultas.asistenciaPorProfesion(f);
        ReporteAsistencia.Conteo total = sumar("Total", porProfesion);
        ReporteAsistencia.Conteo anterior = sumar("Anterior", consultas.asistenciaPorProfesion(f.anterior()));
        YearMonth fin = YearMonth.from(f.hasta());
        YearMonth inicio = fin.minusMonths(5);
        FiltroReporte seisMeses = new FiltroReporte(inicio.atDay(1), f.hasta(), f.idPoliclinico(), f.idProfesional());
        Map<String, ReporteAsistencia.Conteo> porMes = consultas.asistenciaPorMes(seisMeses).stream()
                .collect(Collectors.toMap(ReporteAsistencia.Conteo::etiqueta, c -> c));
        List<ReporteAsistencia.Conteo> evolucion = new ArrayList<>();
        for (YearMonth m = inicio; !m.isAfter(fin); m = m.plusMonths(1)) {
            String clave = m.toString();
            evolucion.add(porMes.getOrDefault(clave, new ReporteAsistencia.Conteo(clave, 0, 0, 0)));
        }
        return new ReporteAsistencia(f, total, anterior, porProfesion, evolucion);
    }

    public ReporteDemanda demanda(FiltroReporte f) {
        return new ReporteDemanda(f, consultas.demanda(f));
    }

    public ReporteCaracterizacion caracterizacion(FiltroReporte f) {
        List<IConsultaReportes.PacienteAtendido> atendidos = consultas.pacientesAtendidos(f);
        LocalDate referencia = f.hasta().isAfter(reloj.hoy()) ? reloj.hoy() : f.hasta();
        List<CategoriaEtaria> categorias = consultas.categorias();
        Map<String, Long> porEdad = new LinkedHashMap<>();
        categorias.forEach(c -> porEdad.put(c.getNombre(), 0L));
        Map<String, Long> porGenero = new TreeMap<>();
        long chilena = 0, otra = 0, adultos = 0, ninos = 0, mayores = 0;
        for (IConsultaReportes.PacienteAtendido p : atendidos) {
            int edad = Period.between(p.fechaNacimiento(), referencia).getYears();
            String cat = categorias.stream().filter(c -> c.incluye(edad)).map(CategoriaEtaria::getNombre).findFirst()
                    .orElse("Sin tramo");
            porEdad.merge(cat, 1L, Long::sum);
            porGenero.merge(p.genero(), 1L, Long::sum);
            if (esChilena(p.nacionalidad())) chilena++; else otra++;
            if (edad < 18) ninos++; else adultos++;
            if (edad >= 60) mayores++;
        }
        return new ReporteCaracterizacion(f, atendidos.size(), lista(porEdad), lista(porGenero),
                List.of(new ReporteCaracterizacion.Categoria("Chilena", chilena), new ReporteCaracterizacion.Categoria("Otra", otra)),
                adultos, ninos, mayores);
    }

    /** RF-24. CU-07 E3: si no hay umbral, se pide configurarlo. */
    public ListadoInasistencia inasistenciaReiterada(FiltroReporte f) {
        int umbral = parametros.umbralInasistencia().orElseThrow(() -> ErrorNegocio.regla("SIN_UMBRAL",
                "Falta configurar el umbral de inasistencia. Pídeselo a la Administración en «Publicación y reglas»."));
        return new ListadoInasistencia(f, umbral, consultas.inasistencias(f));
    }

    public List<CategoriaEtaria> categorias() {
        return consultas.categorias();
    }

    private static boolean esChilena(String nacionalidad) {
        String n = nacionalidad == null ? "" : nacionalidad.trim().toLowerCase();
        return n.startsWith("chilen") || n.equals("chile");
    }

    private static List<ReporteCaracterizacion.Categoria> lista(Map<String, Long> mapa) {
        return mapa.entrySet().stream().map(e -> new ReporteCaracterizacion.Categoria(e.getKey(), e.getValue())).toList();
    }

    private static ReporteAsistencia.Conteo sumar(String etiqueta, List<ReporteAsistencia.Conteo> lista) {
        return new ReporteAsistencia.Conteo(etiqueta, lista.stream().mapToLong(ReporteAsistencia.Conteo::reservadas).sum(),
                lista.stream().mapToLong(ReporteAsistencia.Conteo::asistidas).sum(),
                lista.stream().mapToLong(ReporteAsistencia.Conteo::inasistencias).sum());
    }
}
