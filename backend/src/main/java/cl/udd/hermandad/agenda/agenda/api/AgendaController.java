package cl.udd.hermandad.agenda.agenda.api;

import cl.udd.hermandad.agenda.agenda.aplicacion.AgendaService;
import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.agenda.aplicacion.DatosBloque;
import cl.udd.hermandad.agenda.agenda.dominio.BloqueHorario;
import cl.udd.hermandad.agenda.agenda.dominio.CitaAfectada;
import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** M2 · CU-02. Escritura solo de la Administración (CA-RNF04-1: la TENS/TONS recibe 403). */
@RestController
@RequestMapping("/api")
public class AgendaController {

    public record BloqueRequest(@NotNull(message = "Elige el profesional.") Integer idProfesional,
                                @NotNull(message = "Elige el box.") Integer idBox,
                                @NotNull(message = "Elige el día.") Integer diaSemana,
                                @NotNull(message = "Indica la hora de inicio.") LocalTime horaInicio,
                                @NotNull(message = "Indica la hora de término.") LocalTime horaTermino,
                                @NotNull(message = "Indica desde cuándo rige.") LocalDate vigenciaDesde,
                                @NotNull(message = "Indica hasta cuándo rige.") LocalDate vigenciaHasta) {
        DatosBloque datos() {
            return new DatosBloque(idProfesional, idBox, diaSemana, horaInicio, horaTermino, vigenciaDesde, vigenciaHasta);
        }
    }

    public record BloqueDto(Integer id, Integer idProfesional, String profesional, String profesion, Integer duracion,
                            Integer idBox, String box, Integer idPoliclinico, String policlinico, Integer diaSemana,
                            LocalTime horaInicio, LocalTime horaTermino, LocalDate vigenciaDesde, LocalDate vigenciaHasta,
                            boolean activo) {
        static BloqueDto de(BloqueHorario b) {
            return new BloqueDto(b.getId(), b.getProfesional().getId(), b.getProfesional().nombreCompleto(),
                    b.getProfesional().getProfesion().getNombre(), b.getProfesional().duracionAtencion(),
                    b.getBox().getId(), b.getBox().getNombre(), b.getBox().getPoliclinico().getId(),
                    b.getBox().getPoliclinico().getNombre(), b.getDiaSemana(), b.getHoraInicio(), b.getHoraTermino(),
                    b.getVigenciaDesde(), b.getVigenciaHasta(), b.isActivo());
        }
    }

    public record BloqueoRequest(List<Long> idsCupo, Integer idProfesional, LocalDate fecha, LocalTime desde,
                                 LocalTime hasta, @NotBlank(message = "Escribe el motivo del bloqueo.") String motivo) {
    }

    private final AgendaService servicio;

    public AgendaController(AgendaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/agenda/semana")
    public List<CupoVista> semana(@RequestParam(required = false) Integer profesional,
                                  @RequestParam(required = false) Integer policlinico,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        return servicio.agendaSemanal(profesional, policlinico, desde);
    }

    @GetMapping("/agenda/semana-box")
    public List<CupoVista> semanaBox(@RequestParam Integer box,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        return servicio.agendaPorBox(box, desde);
    }

    @GetMapping("/bloques")
    public List<BloqueDto> bloques(@RequestParam Integer profesional) {
        return servicio.bloquesDelProfesional(profesional).stream().map(BloqueDto::de).toList();
    }

    @PostMapping("/bloques/previsualizar")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public AgendaService.Previsualizacion previsualizar(@Valid @RequestBody BloqueRequest r) {
        return servicio.previsualizar(r.datos());
    }

    @PostMapping("/bloques")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public BloqueDto crear(@Valid @RequestBody BloqueRequest r) {
        return BloqueDto.de(servicio.crearBloque(r.datos()));
    }

    @PutMapping("/bloques/{id}")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public BloqueDto modificar(@PathVariable Integer id, @Valid @RequestBody BloqueRequest r) {
        return BloqueDto.de(servicio.modificarBloque(id, r.datos()));
    }

    @PostMapping("/bloques/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public BloqueDto desactivar(@PathVariable Integer id) {
        return BloqueDto.de(servicio.desactivarBloque(id));
    }

    @GetMapping("/bloques/{id}/afectadas")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public List<CitaAfectada> afectadas(@PathVariable Integer id) {
        return servicio.afectadasAlDesactivar(id);
    }

    @PostMapping("/bloqueos")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public List<CupoVista> bloquear(@Valid @RequestBody BloqueoRequest r) {
        List<Cupo> cupos = servicio.bloquearCupos(r.idsCupo(), r.idProfesional(), r.fecha(), r.desde(), r.hasta(), r.motivo());
        return servicio.vistas(cupos);
    }
}
