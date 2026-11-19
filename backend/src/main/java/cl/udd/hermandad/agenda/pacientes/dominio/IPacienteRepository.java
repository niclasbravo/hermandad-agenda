package cl.udd.hermandad.agenda.pacientes.dominio;

import java.util.List;
import java.util.Optional;

public interface IPacienteRepository {
    Paciente guardar(Paciente paciente);
    Optional<Paciente> buscar(Integer id);
    Optional<Paciente> buscarPorDocumento(TipoDocumento tipo, String numero);
    List<Paciente> buscarPorNumero(String numero);
    Optional<AdultoResponsable> buscarAdulto(TipoDocumento tipo, String numero);
    AdultoResponsable guardarAdulto(AdultoResponsable adulto);
    VerificacionRequisitos guardarVerificacion(VerificacionRequisitos verificacion);
    DocumentoAdjunto guardarDocumento(DocumentoAdjunto documento);
    Optional<DocumentoAdjunto> buscarDocumento(Integer id);
    List<DocumentoAdjunto> documentosDe(Integer idPaciente);
    long contar();
}
