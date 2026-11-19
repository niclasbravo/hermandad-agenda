package cl.udd.hermandad.agenda.listaespera.api;

import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.compartido.Textos;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import cl.udd.hermandad.agenda.listaespera.dominio.InscripcionEspera;
import cl.udd.hermandad.agenda.listaespera.dominio.MotivoRetiro;
import cl.udd.hermandad.agenda.listaespera.dominio.MovimientoEspera;
import cl.udd.hermandad.agenda.pacientes.dominio.Telefono;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/** M5 · CU-04. La TENS/TONS ve y maneja la lista de su policlínico base. */
@RestController
@RequestMapping("/api")
public class ListaEsperaController {

    public record InscripcionRequest(@NotNull(message = "Falta el paciente.") Integer idPaciente,
                                     @NotNull(message = "Elige el policlínico.") Integer idPoliclinico,
                                     @NotNull(message = "Elige la profesión.") Integer idProfesion) {
    }

    public record AdelantarRequest(String motivo, Integer posicion) {
    }

    public record RetirarRequest(@NotNull(message = "Elige el motivo.") MotivoRetiro motivo, String detalle, Long idCita) {
    }

    public record MovimientoDto(String tipo, String motivo, Integer idUsuario, LocalDateTime fechaHora) {
    }

    public record InscripcionDto(Integer id, int posicion, Integer idPaciente, String paciente, String iniciales,
                                 String documento, String telefono, Integer idPoliclinico, String policlinico,
                                 Integer idProfesion, String profesion, LocalDateTime fechaInscripcion,
                                 LocalDateTime fechaOrden, boolean adelantada, List<MovimientoDto> movimientos) {
    }

    public record AvisoDto(Integer id, Long idCupo, LocalDate fecha, LocalTime hora, String profesion, String profesional,
                           String box, Integer idInscripcion, Integer idPaciente, String primera, String telefono,
                           String mensaje) {
    }

    private final ListaEsperaService servicio;

    public ListaEsperaController(ListaEsperaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/lista-espera")
    public List<InscripcionDto> listar(@RequestParam(required = false) Integer policlinico,
                                       @RequestParam(required = false) Integer profesion) {
        Integer pol = SesionActual.policlinicoFiltrado(policlinico);
        Map<Integer, Integer> posiciones = new HashMap<>();
        List<InscripcionDto> lista = new ArrayList<>();
        for (InscripcionEspera i : servicio.listar(pol, profesion)) {
            int clave = Objects.hash(i.getPoliclinico().getId(), i.getProfesion().getId());
            int pos = posiciones.merge(clave, 1, Integer::sum);
            lista.add(dto(i, pos));
        }
        return lista;
    }

    @PostMapping("/lista-espera")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENS')")
    public InscripcionDto inscribir(@Valid @RequestBody InscripcionRequest r) {
        return dto(servicio.inscribir(r.idPaciente(), r.idPoliclinico(), r.idProfesion()), 0);
    }

    @PostMapping("/lista-espera/{id}/adelantar")
    @PreAuthorize("hasRole('TENS')")
    public InscripcionDto adelantar(@PathVariable Integer id, @RequestBody AdelantarRequest r) {
        return dto(servicio.adelantar(id, r.motivo(), r.posicion()), 0);
    }

    @PostMapping("/lista-espera/{id}/retirar")
    @PreAuthorize("hasRole('TENS')")
    public InscripcionDto retirar(@PathVariable Integer id, @Valid @RequestBody RetirarRequest r) {
        return dto(servicio.retirar(id, r.motivo(), r.detalle(), r.idCita()), 0);
    }

    @GetMapping("/avisos-cupo")
    public List<AvisoDto> avisos(@RequestParam(required = false) Integer policlinico) {
        Integer pol = SesionActual.policlinicoFiltrado(policlinico);
        return servicio.avisosPendientes(pol).stream().map(v -> {
            var c = v.cupo();
            var p = v.primera().getPaciente();
            String profesion = c.getBloque().getProfesional().getProfesion().getNombre();
            String tel = Telefono.formatear(p.getTelefonoCelular());
            String msj = "Se liberó un cupo de " + profesion + " a las " + Textos.hora2(c.getHoraInicio())
                    + ". Primera en la lista: " + p.nombreCompleto() + ", " + tel + ".";
            return new AvisoDto(v.aviso().getId(), c.getId(), c.getFecha(), c.getHoraInicio(), profesion,
                    c.getBloque().getProfesional().nombreCompleto(), c.getBloque().getBox().getNombre(),
                    v.primera().getId(), p.getId(), p.nombreCompleto(), tel, msj);
        }).toList();
    }

    @PostMapping("/avisos-cupo/{id}/atender")
    @PreAuthorize("hasRole('TENS')")
    public Map<String, Object> atender(@PathVariable Integer id) {
        servicio.atenderAviso(id);
        return Map.of("atendido", true);
    }

    static InscripcionDto dto(InscripcionEspera i, int posicion) {
        var p = i.getPaciente();
        boolean adelantada = i.getMovimientos().stream().anyMatch(m -> m.getTipo().name().equals("ADELANTO"));
        return new InscripcionDto(i.getId(), posicion, p.getId(), p.nombreCompleto(), p.iniciales(), p.getDocumento().formatear(),
                Telefono.formatear(p.getTelefonoCelular()), i.getPoliclinico().getId(), i.getPoliclinico().getNombre(),
                i.getProfesion().getId(), i.getProfesion().getNombre(), i.getFechaInscripcion(), i.getFechaOrden(), adelantada,
                i.getMovimientos().stream().map(ListaEsperaController::mov).toList());
    }

    static MovimientoDto mov(MovimientoEspera m) {
        return new MovimientoDto(m.getTipo().name(), m.getMotivo(), m.getIdUsuario(), m.getFechaHora());
    }
}
