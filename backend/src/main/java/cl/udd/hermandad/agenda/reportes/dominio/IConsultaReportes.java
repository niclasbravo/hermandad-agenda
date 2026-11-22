package cl.udd.hermandad.agenda.reportes.dominio;

import java.time.LocalDate;
import java.util.List;

/** Consultas en vivo sobre los registros vigentes (RNF-12), sin tablas propias. */
public interface IConsultaReportes {

    record PacienteAtendido(Integer idPaciente, java.time.LocalDate fechaNacimiento, String genero, String nacionalidad) {
    }

    List<ReporteOcupacion.Fila> ocupacion(FiltroReporte f, LocalDate hoy);
    List<ReporteAsistencia.Conteo> asistenciaPorProfesion(FiltroReporte f);
    List<ReporteAsistencia.Conteo> asistenciaPorMes(FiltroReporte f);
    List<ReporteDemanda.Fila> demanda(FiltroReporte f);
    /** Inscripciones EN_ESPERA hoy, cada una contada una vez (no depende del período). */
    long enEsperaTotal(FiltroReporte f);
    List<PacienteAtendido> pacientesAtendidos(FiltroReporte f);
    List<ListadoInasistencia.Fila> inasistencias(FiltroReporte f);
    List<CategoriaEtaria> categorias();
    CategoriaEtaria guardarCategoria(CategoriaEtaria c);
    void eliminarCategoria(CategoriaEtaria c);
}
