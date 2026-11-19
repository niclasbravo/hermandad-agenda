package cl.udd.hermandad.agenda.citas.api;

import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.citas.api.CitaDtos.ComprobanteDto;
import cl.udd.hermandad.agenda.citas.aplicacion.ReservaEnLineaService;
import cl.udd.hermandad.agenda.pacientes.api.PacienteDtos.AdultoRequest;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.dominio.DocumentoAdjunto;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoAdjunto;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** API pública de reserva en línea (CU-08). Ninguna respuesta incluye datos de la ficha (RES-06). */
@RestController
@RequestMapping("/api/publico")
public class ReservaPublicaController {

    public static final String CABECERA_TOKEN = "X-Token-Reserva";

    public record IdentificacionRequest(TipoDocumento tipoDocumento,
                                        @NotBlank(message = "Escribe tu RUT o documento.") String numero,
                                        @NotNull(message = "Escribe tu fecha de nacimiento.") LocalDate fechaNacimiento) {
    }

    public record RegistroRequest(@NotBlank(message = "Escribe tus nombres.") @Size(max = 80) String nombres,
                                  @NotBlank(message = "Escribe tus apellidos.") @Size(max = 80) String apellidos,
                                  @NotBlank(message = "Escribe tu número de celular.") @Size(max = 20) String telefonoCelular,
                                  @Size(max = 120) String correo,
                                  @NotBlank(message = "Escribe tu dirección.") @Size(max = 150) String direccion,
                                  @NotBlank(message = "Elige el género.") @Size(max = 30) String genero,
                                  @NotBlank(message = "Escribe la nacionalidad.") @Size(max = 60) String nacionalidad,
                                  @Valid AdultoRequest adulto) {
    }

    public record ReservaRequest(@NotNull(message = "Elige un horario.") Long idCupo) {
    }

    public record CupoPublico(Long id, LocalDate fecha, LocalTime hora, int duracion, Integer idProfesional, String profesional,
                              String profesion, Integer idPoliclinico, String policlinico, String direccion, String box) {
        static CupoPublico de(CupoVista c) {
            return new CupoPublico(c.id(), c.fecha(), c.horaInicio(), c.duracion(), c.idProfesional(), c.profesional(),
                    c.profesion(), c.idPoliclinico(), c.policlinico(), c.direccion(), c.box());
        }
    }

    private final ReservaEnLineaService servicio;

    public ReservaPublicaController(ReservaEnLineaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/identificacion")
    public ReservaEnLineaService.Identificacion identificar(@Valid @RequestBody IdentificacionRequest r, HttpServletRequest req) {
        return servicio.identificar(r.tipoDocumento() == null ? TipoDocumento.RUT : r.tipoDocumento(), r.numero(),
                r.fechaNacimiento(), ip(req));
    }

    @PostMapping("/pacientes")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaEnLineaService.Identificacion registrar(@RequestHeader(name = CABECERA_TOKEN, required = false) String token,
                                                         @Valid @RequestBody RegistroRequest r, HttpServletRequest req) {
        DatosPaciente d = new DatosPaciente(null, null, r.nombres(), r.apellidos(), null, r.telefonoCelular(), r.correo(),
                r.direccion(), r.genero(), r.nacionalidad(),
                r.adulto() == null ? null : new DatosPaciente.DatosAdulto(r.adulto().tipoDocumento(),
                        r.adulto().numeroDocumento(), r.adulto().nombreCompleto(), r.adulto().telefono(), r.adulto().relacion()));
        return servicio.registrar(token, d, ip(req));
    }

    @GetMapping("/cupos")
    public List<CupoPublico> cupos(@RequestParam(required = false) Integer policlinico,
                                   @RequestParam(required = false) Integer profesion,
                                   @RequestParam(required = false) Integer profesional,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        return servicio.cupos(policlinico, profesion, profesional, desde).stream().map(CupoPublico::de).toList();
    }

    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ComprobanteDto reservar(@RequestHeader(name = CABECERA_TOKEN, required = false) String token,
                                   @Valid @RequestBody ReservaRequest r) {
        return ComprobanteDto.de(servicio.reservar(token, r.idCupo()));
    }

    @PostMapping(path = "/reservas/{id}/certificados", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> certificado(@RequestHeader(name = CABECERA_TOKEN, required = false) String token,
                                           @PathVariable Long id, @RequestParam("tipo") TipoAdjunto tipo,
                                           @RequestParam("archivo") MultipartFile archivo) throws IOException {
        DocumentoAdjunto d = servicio.subirCertificado(token, id, tipo, archivo.getOriginalFilename(), archivo.getBytes());
        return Map.of("id", d.getId(), "tipo", d.getTipo(), "nombreArchivo", d.getNombreArchivo());
    }

    static String ip(HttpServletRequest req) {
        String real = req.getHeader("X-Real-IP");
        return real == null || real.isBlank() ? req.getRemoteAddr() : real.trim();
    }
}
