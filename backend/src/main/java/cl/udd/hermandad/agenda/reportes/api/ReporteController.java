package cl.udd.hermandad.agenda.reportes.api;

import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.reportes.aplicacion.CategoriaEtariaService;
import cl.udd.hermandad.agenda.reportes.aplicacion.ReporteService;
import cl.udd.hermandad.agenda.reportes.dominio.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** M8 · CU-07. Gestión: Administración. Inasistencia reiterada: ambos (la TENS/TONS solo su policlínico). */
@RestController
@RequestMapping("/api")
public class ReporteController {

    private final ReporteService servicio;
    private final CategoriaEtariaService categorias;

    public ReporteController(ReporteService servicio, CategoriaEtariaService categorias) {
        this.servicio = servicio;
        this.categorias = categorias;
    }

    @GetMapping("/reportes/ocupacion")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public Map<String, Object> ocupacion(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(required = false) Integer policlinico,
                                         @RequestParam(required = false) Integer profesional) {
        ReporteOcupacion r = servicio.ocupacion(new FiltroReporte(desde, hasta, policlinico, profesional));
        Map<String, Object> m = base(r);
        m.put("ofrecidos", r.ofrecidos());
        m.put("reservados", r.reservados());
        m.put("disponibles", r.disponibles());
        m.put("porcentajeOcupacion", r.porcentajeOcupacion());
        m.put("anterior", Map.of("ofrecidos", r.ofrecidosAnterior(), "reservados", r.reservadosAnterior(),
                "porcentajeOcupacion", r.porcentajeOcupacionAnterior()));
        m.put("filas", r.filas().stream().map(f -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("idPoliclinico", f.idPoliclinico());
            x.put("policlinico", f.policlinico());
            x.put("idProfesional", f.idProfesional());
            x.put("profesional", f.profesional());
            x.put("profesion", f.profesion());
            x.put("ofrecidos", f.ofrecidos());
            x.put("reservados", f.reservados());
            x.put("disponibles", f.disponibles());
            x.put("porcentajeOcupacion", f.porcentajeOcupacion());
            return x;
        }).toList());
        return m;
    }

    @GetMapping("/reportes/asistencia")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public Map<String, Object> asistencia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                          @RequestParam(required = false) Integer policlinico,
                                          @RequestParam(required = false) Integer profesional) {
        ReporteAsistencia r = servicio.asistencia(new FiltroReporte(desde, hasta, policlinico, profesional));
        Map<String, Object> m = base(r);
        m.put("total", conteo(r.total()));
        m.put("anterior", conteo(r.anterior()));
        m.put("porProfesion", r.porProfesion().stream().map(ReporteController::conteo).toList());
        m.put("evolucion", r.evolucion().stream().map(ReporteController::conteo).toList());
        return m;
    }

    @GetMapping("/reportes/demanda")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public Map<String, Object> demanda(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       @RequestParam(required = false) Integer policlinico,
                                       @RequestParam(required = false) Integer profesional) {
        ReporteDemanda r = servicio.demanda(new FiltroReporte(desde, hasta, policlinico, profesional));
        Map<String, Object> m = base(r);
        m.put("reservas", r.reservas());
        m.put("enEspera", r.enEspera());
        m.put("filas", r.filas().stream().map(f -> Map.of("idProfesional", f.idProfesional(), "profesional", f.profesional(),
                "profesion", f.profesion(), "policlinicos", f.policlinicos(), "reservas", f.reservas(),
                "enEspera", f.enEspera(), "demanda", f.demanda())).toList());
        return m;
    }

    @GetMapping("/reportes/caracterizacion")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public Map<String, Object> caracterizacion(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                               @RequestParam(required = false) Integer policlinico,
                                               @RequestParam(required = false) Integer profesional) {
        ReporteCaracterizacion r = servicio.caracterizacion(new FiltroReporte(desde, hasta, policlinico, profesional));
        Map<String, Object> m = base(r);
        m.put("total", r.total());
        m.put("porEdad", r.porEdad());
        m.put("porGenero", r.porGenero());
        m.put("porNacionalidad", r.porNacionalidad());
        m.put("adultos", r.adultos());
        m.put("ninos", r.ninos());
        m.put("adultosMayores", r.adultosMayores());
        m.put("porcentajeAdultosMayores", r.porcentajeAdultosMayores());
        return m;
    }

    @GetMapping("/reportes/inasistencia-reiterada")
    public Map<String, Object> inasistencia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(required = false) Integer policlinico) {
        Integer pol = SesionActual.policlinicoFiltrado(policlinico);
        ListadoInasistencia r = servicio.inasistenciaReiterada(new FiltroReporte(desde, hasta, pol, null));
        Map<String, Object> m = base(r);
        m.put("umbral", r.umbral());
        m.put("idPoliclinico", pol);
        m.put("filas", r.filas());
        return m;
    }

    @GetMapping("/categorias-etarias")
    public List<CategoriaEtaria> listarCategorias() {
        return servicio.categorias();
    }

    @PutMapping("/categorias-etarias")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public List<CategoriaEtaria> guardarCategorias(@RequestBody List<CategoriaEtariaService.Datos> datos) {
        return categorias.guardar(datos);
    }

    private static Map<String, Object> base(Reporte r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("desde", r.getFiltro().desde());
        m.put("hasta", r.getFiltro().hasta());
        m.put("sinDatos", r.sinDatos());
        if (r.sinDatos()) {
            m.put("mensaje", "No hay registros en el período seleccionado.");
        }
        return m;
    }

    private static Map<String, Object> conteo(ReporteAsistencia.Conteo c) {
        return Map.of("etiqueta", c.etiqueta(), "reservadas", c.reservadas(), "asistidas", c.asistidas(),
                "inasistencias", c.inasistencias(), "porcentajeInasistencia", c.porcentajeInasistencia());
    }
}
