package cl.udd.hermandad.agenda.confirmaciones.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IMensajeRepository {
    Mensaje guardar(Mensaje mensaje);
    Optional<Mensaje> buscar(Long id);
    Optional<Mensaje> buscarPorIdExterno(String idExterno);
    List<Long> pendientes(LocalDateTime ahora, int limite);
    List<Mensaje> deCita(Long idCita);
    List<Mensaje> programadosDeCita(Long idCita);
    List<Mensaje> enviadosA(String telefono, int limite);
    List<String> telefonosConMensajes();
    RespuestaPaciente guardarRespuesta(RespuestaPaciente respuesta);
    List<RespuestaPaciente> respuestasDeCita(Long idCita);
    LlamadaConfirmacion guardarLlamada(LlamadaConfirmacion llamada);
    List<LlamadaConfirmacion> llamadasDeCita(Long idCita);
    Optional<PlantillaMensaje> plantilla(String nombreProveedor);
    PlantillaMensaje guardarPlantilla(PlantillaMensaje plantilla);
}
