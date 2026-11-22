package cl.udd.hermandad.agenda.soporte;

import cl.udd.hermandad.agenda.agenda.dominio.*;
import cl.udd.hermandad.agenda.citas.dominio.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositorios en memoria: AgendamientoService solo conoce ICitaRepository e ICupoRepository,
 * así que se prueba sin base de datos (patrón Repository).
 */
public final class RepositoriosEnMemoria {

    public final Citas citas = new Citas();
    public final Cupos cupos = new Cupos();

    public class Citas implements ICitaRepository {
        public final Map<Long, Cita> datos = new LinkedHashMap<>();
        public final List<SerieSesiones> series = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Cita guardar(Cita cita) {
            if (cita.estaVigente() || cita.getEstado() == EstadoCita.ASISTIDA || cita.getEstado() == EstadoCita.INASISTIDA) {
                boolean otra = datos.values().stream().anyMatch(c -> c != cita && c.getEstado() != EstadoCita.ANULADA
                        && c.getCupo().getId().equals(cita.getCupo().getId()));
                if (otra) {
                    throw new DataIntegrityViolationException("Duplicate entry for key 'cita.uq_cita_cupo_activo'");
                }
            }
            if (cita.getId() == null) {
                ReflectionTestUtils.setField(cita, "id", ids.incrementAndGet());
            }
            datos.put(cita.getId(), cita);
            return cita;
        }

        @Override
        public Optional<Cita> buscar(Long id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Cita> buscarDelDia(Integer idPoliclinico, LocalDate fecha) {
            return datos.values().stream().filter(c -> c.getCupo().getFecha().equals(fecha)).toList();
        }

        @Override
        public List<Cita> buscarPorPaciente(Integer idPaciente) {
            return datos.values().stream().filter(c -> c.getPaciente().getId().equals(idPaciente)).toList();
        }

        @Override
        public long contarActivasEnLinea(Integer idPaciente, LocalDate desde) {
            return datos.values().stream().filter(c -> c.getPaciente().getId().equals(idPaciente)
                    && c.getCanalReserva() == CanalReserva.EN_LINEA && c.estaVigente()
                    && !c.getCupo().getFecha().isBefore(desde)).count();
        }

        @Override
        public List<Cita> pendientesDeVerificacion(Integer idPaciente) {
            return datos.values().stream().filter(c -> c.getPaciente().getId().equals(idPaciente)
                    && c.getEstado() == EstadoCita.PENDIENTE_VERIFICACION).toList();
        }

        @Override
        public Optional<Cita> vigenteDeCupo(Long idCupo) {
            return datos.values().stream().filter(c -> c.getCupo().getId().equals(idCupo) && c.getEstado() != EstadoCita.ANULADA).findFirst();
        }

        @Override
        public SerieSesiones guardarSerie(SerieSesiones serie) {
            ReflectionTestUtils.setField(serie, "id", series.size() + 1);
            series.add(serie);
            return serie;
        }
    }

    public class Cupos implements ICupoRepository {
        public final Map<Long, Cupo> datos = new LinkedHashMap<>();

        public Cupo agregar(Cupo c) {
            datos.put(c.getId(), c);
            return c;
        }

        @Override
        public Optional<Cupo> obtenerParaReserva(Long idCupo) {
            return buscar(idCupo);
        }

        @Override
        public Optional<Cupo> buscar(Long idCupo) {
            return Optional.ofNullable(datos.get(idCupo));
        }

        @Override
        public List<Cupo> buscarVarios(Collection<Long> ids) {
            return ids.stream().map(datos::get).filter(Objects::nonNull).toList();
        }

        @Override
        public boolean tieneCitaVigente(Long idCupo) {
            return citas.vigenteDeCupo(idCupo).isPresent();
        }

        @Override
        public Set<Long> conCitaVigente(Collection<Long> idsCupo) {
            Set<Long> s = new HashSet<>();
            idsCupo.forEach(id -> { if (tieneCitaVigente(id)) s.add(id); });
            return s;
        }

        @Override
        public List<Cupo> buscarDisponibles(FiltroCupos f, LocalDateTime ahora) {
            return datos.values().stream()
                    .filter(c -> c.estaDisponible(ahora, tieneCitaVigente(c.getId())))
                    .filter(c -> !f.soloWeb() || c.isHabilitadoWeb())
                    .filter(c -> f.idProfesional() == null || c.getBloque().getProfesional().getId().equals(f.idProfesional()))
                    .sorted(Comparator.comparing(Cupo::inicio)).toList();
        }

        @Override
        public List<Cupo> guardarTodos(List<Cupo> cupos) {
            cupos.forEach(this::guardar);
            return cupos;
        }

        @Override
        public Cupo guardar(Cupo cupo) {
            return agregar(cupo);
        }

        @Override
        public Bloqueo guardarBloqueo(Bloqueo bloqueo) {
            return bloqueo;
        }

        @Override
        public List<Cupo> delBloque(Integer idBloque, LocalDate desde, LocalDate hasta) {
            return datos.values().stream().filter(c -> c.getBloque().getId().equals(idBloque)).toList();
        }

        @Override
        public List<Cupo> deLaSemana(Integer idProfesional, Integer idPoliclinico, Integer idBox, LocalDate desde, LocalDate hasta,
                                     LocalDate hoy) {
            return datos.values().stream().filter(c -> !c.getFecha().isBefore(desde) && !c.getFecha().isAfter(hasta)).toList();
        }

        @Override
        public Optional<Cupo> buscarPorProfesionalFechaHora(Integer idProfesional, LocalDate fecha, LocalTime hora) {
            return datos.values().stream().filter(c -> c.getBloque().getProfesional().getId().equals(idProfesional)
                    && c.getFecha().equals(fecha) && c.getHoraInicio().equals(hora)).findFirst();
        }

        @Override
        public List<Cupo> futurosDeBloquesActivos(LocalDate desde) {
            return datos.values().stream().filter(c -> !c.getFecha().isBefore(desde)).toList();
        }

        @Override
        public List<CitaAfectada> citasVigentes(Collection<Long> idsCupo) {
            return List.of();
        }

        @Override
        public Map<Long, String> pacientesDeCupos(Collection<Long> idsCupo) {
            return Map.of();
        }
    }
}
