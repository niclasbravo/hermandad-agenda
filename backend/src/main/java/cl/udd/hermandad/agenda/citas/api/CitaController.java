package cl.udd.hermandad.agenda.citas.api;

import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.agenda.dominio.FiltroCupos;
import cl.udd.hermandad.agenda.citas.api.CitaDtos.CitaDto;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.citas.dominio.OrigenAnulacion;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** M4 · CU-04. Reservar, anular, reagendar y series: solo TENS/TONS (la Administración no reserva). */
@RestController
@RequestMapping("/api")
public class CitaController {

    public record ReservaRequest(@NotNull(message = "Falta el paciente.") Integer idPaciente,
                                 @NotNull(message = "Elige un horario.") Long idCupo) {
    }

    public record SerieRequest(@NotNull(message = "Falta el paciente.") Integer idPaciente, Integer idProfesional,
                               @NotNull(message = "Elige el primer horario.") Long idCupoPrimero,
                               @NotNull(message = "Indica la cantidad de sesiones.") Integer cantidad,
                               List<LocalDate> fechasAceptadas) {
    }

    public record AnularRequest(OrigenAnulacion origen, String motivo) {
    }

    public record ReagendarRequest(@NotNull(message = "Elige el nuevo horario.") Long idCupoNuevo) {
    }

    public record ResolverRequest(@NotNull List<AgendamientoService.AccionAfectada> acciones) {
    }

    public record ReservaResponse(CitaDto cita, List<Integer> inscripcionesEnEspera) {
    }

    private final AgendamientoService servicio;
    private final ListaEsperaService listaEspera;
    private final Reloj reloj;

    public CitaController(AgendamientoService servicio, ListaEsperaService listaEspera, Reloj reloj) {
        this.servicio = servicio;
        this.listaEspera = listaEspera;
        this.reloj = reloj;
    }

    @GetMapping("/cupos/disponibles")
    public List<CupoVista> disponibles(@RequestParam(required = false) Integer policlinico,
                                       @RequestParam(required = false) Integer profesion,
                                       @RequestParam(required = false) Integer profesional,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.consultarCupos(new FiltroCupos(policlinico, profesion, profesional, desde, hasta, false));
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENS')")
    public ReservaResponse reservar(@Valid @RequestBody ReservaRequest r) {
        Cita c = servicio.reservar(r.idPaciente(), r.idCupo());
        return respuesta(c);
    }

    @PostMapping("/citas/serie/previsualizar")
    @PreAuthorize("hasRole('TENS')")
    public AgendamientoService.PrevisualizacionSerie previsualizarSerie(@Valid @RequestBody SerieRequest r) {
        return servicio.previsualizarSerie(r.idProfesional(), r.idCupoPrimero(), r.cantidad());
    }

    @PostMapping("/citas/serie")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENS')")
    public List<CitaDto> reservarSerie(@Valid @RequestBody SerieRequest r) {
        return servicio.reservarSerie(r.idPaciente(), r.idProfesional(), r.idCupoPrimero(), r.cantidad(), r.fechasAceptadas())
                .stream().map(c -> CitaDto.de(c, reloj.hoy())).toList();
    }

    @PostMapping("/citas/{id}/anular")
    @PreAuthorize("hasRole('TENS')")
    public CitaDto anular(@PathVariable Long id, @RequestBody(required = false) AnularRequest r) {
        String motivo = r == null ? null : r.motivo();
        return CitaDto.de(servicio.anular(id, OrigenAnulacion.TENS, motivo), reloj.hoy());
    }

    @PostMapping("/citas/{id}/reagendar")
    @PreAuthorize("hasRole('TENS')")
    public ReservaResponse reagendar(@PathVariable Long id, @Valid @RequestBody ReagendarRequest r) {
        return respuesta(servicio.reagendar(id, r.idCupoNuevo()));
    }

    @PostMapping("/bloques/{id}/resolver-afectadas")
    @PreAuthorize("hasRole('ADMINISTRACION')")
    public List<CitaDto> resolverAfectadas(@PathVariable Integer id, @Valid @RequestBody ResolverRequest r) {
        return servicio.resolverAfectadas(r.acciones()).stream().map(c -> CitaDto.de(c, reloj.hoy())).toList();
    }

    @GetMapping("/pacientes/{id}/historial")
    public List<CitaDto> historial(@PathVariable Integer id) {
        return servicio.historial(id).stream().map(c -> CitaDto.de(c, reloj.hoy())).toList();
    }

    private ReservaResponse respuesta(Cita c) {
        Integer pol = c.getCupo().getBloque().getBox().getPoliclinico().getId();
        Integer prof = c.getCupo().getBloque().getProfesional().getProfesion().getId();
        List<Integer> enEspera = listaEspera.delPaciente(c.getPaciente().getId()).stream()
                .filter(i -> i.getPoliclinico().getId().equals(pol) && i.getProfesion().getId().equals(prof))
                .map(i -> i.getId()).toList();
        return new ReservaResponse(CitaDto.de(c, reloj.hoy()), enEspera);
    }

    static Map<String, Object> vacio() {
        return Map.of();
    }

    static boolean esTens() {
        return SesionActual.usuario().esTens();
    }
}
