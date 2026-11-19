package cl.udd.hermandad.agenda.servicios;

import cl.udd.hermandad.agenda.agenda.dominio.BloqueHorario;
import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesional;
import cl.udd.hermandad.agenda.citas.aplicacion.AgendamientoService;
import cl.udd.hermandad.agenda.citas.dominio.*;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.confirmaciones.aplicacion.NotificacionService;
import cl.udd.hermandad.agenda.listaespera.aplicacion.ListaEsperaService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import cl.udd.hermandad.agenda.soporte.RepositoriosEnMemoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static cl.udd.hermandad.agenda.soporte.Fabrica.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reglas de AgendamientoService con repositorios en memoria (patrón Repository) y colaboradores simulados. */
class AgendamientoServiceTest {

    private final LocalDateTime ahora = LocalDateTime.of(2026, 10, 12, 8, 0); // lunes
    private final LocalDate martes = LocalDate.of(2026, 10, 13);
    private RepositoriosEnMemoria repos;
    private PacienteService pacientes;
    private NotificacionService notificaciones;
    private ListaEsperaService listaEspera;
    private ParametroService parametros;
    private AuditoriaService auditoria;
    private AgendamientoService servicio;
    private Paciente habilitado;
    private Paciente sinVerificar;
    private BloqueHorario bloque;
    private Profesional profesional;

    @BeforeEach
    void preparar() {
        repos = new RepositoriosEnMemoria();
        pacientes = mock(PacienteService.class);
        notificaciones = mock(NotificacionService.class);
        listaEspera = mock(ListaEsperaService.class);
        parametros = mock(ParametroService.class);
        auditoria = mock(AuditoriaService.class);
        when(parametros.topeReservasWeb()).thenReturn(Optional.empty());
        servicio = new AgendamientoService(repos.citas, repos.cupos, pacientes, notificaciones, listaEspera, parametros,
                auditoria, Reloj.fijo(ahora));
        habilitado = paciente("12345678-5", LocalDate.of(1952, 5, 1), true, ahora);
        sinVerificar = paciente("9876543-3", LocalDate.of(1960, 5, 1), false, ahora);
        when(pacientes.obtener(habilitado.getId())).thenReturn(habilitado);
        when(pacientes.obtener(sinVerificar.getId())).thenReturn(sinVerificar);
        Policlinico pol = policlinico();
        profesional = profesional(profesion("Kinesiología", 30), pol, false);
        bloque = bloque(profesional, box(pol, 2), 2, "09:00", "12:00", martes.minusMonths(1), martes.plusMonths(3));
        iniciarSesion(PerfilUsuario.TENS);
    }

    @AfterEach
    void limpiar() {
        cerrarSesion();
    }

    private Cupo cupo(LocalDate fecha, String hora, boolean web) {
        Cupo c = cl.udd.hermandad.agenda.soporte.Fabrica.cupo(bloque, fecha, hora, 30);
        c.habilitarWeb(web);
        return repos.cupos.agregar(c);
    }

    @Test
    void caRf15_1_reservaYElCupoDejaDeMostrarse() {
        Cupo c = cupo(martes, "09:00", false);
        Cita cita = servicio.reservar(habilitado.getId(), c.getId());
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.RESERVADA);
        assertThat(cita.getCanalReserva()).isEqualTo(CanalReserva.TENS);
        assertThat(repos.cupos.tieneCitaVigente(c.getId())).isTrue();
        assertThat(servicio.consultarCupos(new cl.udd.hermandad.agenda.agenda.dominio.FiltroCupos(null, null, null, null, null, false)))
                .noneMatch(v -> v.id().equals(c.getId()));
        verify(notificaciones).programarConfirmacion(cita);
        verify(notificaciones).programarRecordatorios(cita);
        verify(auditoria).registrarComo(eq(2), eq(AccionAuditoria.CREAR), eq("cita"), eq(cita.getId()), anyString());
    }

    @Test
    void caRf15_2_pacienteSinVerificacionRecibe422() {
        Cupo c = cupo(martes, "09:00", false);
        assertThatThrownBy(() -> servicio.reservar(sinVerificar.getId(), c.getId()))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.REGLA_NEGOCIO));
        assertThat(repos.citas.datos).isEmpty();
        verifyNoInteractions(notificaciones);
    }

    @Test
    void caRf15_3_cupoYaTomadoRecibe409() {
        Cupo c = cupo(martes, "09:00", false);
        servicio.reservar(habilitado.getId(), c.getId());
        assertThatThrownBy(() -> servicio.reservar(habilitado.getId(), c.getId()))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.CONFLICTO);
                    assertThat(e.getMessage()).isEqualTo("Otra persona tomó este horario hace un momento. Elige otro.");
                });
    }

    @Test
    void cupoNoPublicadoNoSeReservaPorLaTens() {
        Cupo c = cupo(martes.plusWeeks(3), "09:00", false);
        assertThatThrownBy(() -> servicio.reservar(habilitado.getId(), c.getId()))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.CONFLICTO));
    }

    @Test
    void enLineaSinVerificacionQuedaPendiente() {
        cerrarSesion();
        Cupo c = cupo(martes, "09:00", true);
        Cita cita = servicio.reservarEnLinea(sinVerificar.getId(), c.getId());
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE_VERIFICACION);
        assertThat(cita.getCanalReserva()).isEqualTo(CanalReserva.EN_LINEA);
        assertThat(cita.getIdUsuarioReserva()).isNull();
        verify(auditoria).registrarComo(isNull(), eq(AccionAuditoria.CREAR), eq("cita"), anyLong(), anyString());
    }

    @Test
    void enLineaRechazaCupoNoWeb() {
        Cupo c = cupo(martes, "09:00", false);
        assertThatThrownBy(() -> servicio.reservarEnLinea(habilitado.getId(), c.getId()))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> assertThat(e.getCodigo()).isEqualTo("CUPO_NO_WEB"));
    }

    @Test
    void enLineaRespetaElTope() {
        when(parametros.topeReservasWeb()).thenReturn(Optional.of(1));
        servicio.reservarEnLinea(habilitado.getId(), cupo(martes, "09:00", true).getId());
        Cupo otro = cupo(martes, "09:30", true);
        assertThatThrownBy(() -> servicio.reservarEnLinea(habilitado.getId(), otro.getId()))
                .isInstanceOfSatisfying(ErrorNegocio.class, e -> {
                    assertThat(e.getTipo()).isEqualTo(ErrorNegocio.Tipo.REGLA_NEGOCIO);
                    assertThat(e.getCodigo()).isEqualTo("TOPE_WEB");
                    assertThat(e.getMessage()).startsWith("Ya tienes 1 hora reservada en línea");
                });
    }

    @Test
    void anularLiberaElCupoCancelaMensajesYAvisaALaListaDeEspera() {
        Cupo c = cupo(martes, "09:00", false);
        Cita cita = servicio.reservar(habilitado.getId(), c.getId());
        Cita anulada = servicio.anular(cita.getId(), OrigenAnulacion.TENS, "Avisó por teléfono");
        assertThat(anulada.getEstado()).isEqualTo(EstadoCita.ANULADA);
        assertThat(repos.cupos.tieneCitaVigente(c.getId())).isFalse();
        verify(notificaciones).cancelarProgramados(cita);
        verify(notificaciones, never()).enviarAnulacion(any(), anyBoolean());
        verify(listaEspera).avisarCupoLiberado(c, cita.getId());
        verify(auditoria).registrarComo(eq(2), eq(AccionAuditoria.ANULAR), eq("cita"), eq(cita.getId()), contains("TENS"));
        // RN-07: el cupo se puede volver a reservar.
        assertThat(servicio.reservar(habilitado.getId(), c.getId()).getEstado()).isEqualTo(EstadoCita.RESERVADA);
    }

    @Test
    void anulacionPorMensajeDelPacienteEnviaAvisoDeAnulacion() {
        Cita cita = servicio.reservar(habilitado.getId(), cupo(martes, "09:00", false).getId());
        cerrarSesion();
        servicio.anular(cita.getId(), OrigenAnulacion.PACIENTE_MENSAJE, "No podré asistir");
        verify(notificaciones).enviarAnulacion(cita, false);
    }

    @Test
    void d06_serieMuestraLasFechasSinCupoYReservaSoloLasAceptadas() {
        Cupo primero = cupo(martes, "11:30", false);
        cupo(martes.plusWeeks(1), "11:30", false);
        Cupo tomado = cupo(martes.plusWeeks(2), "11:30", false);
        // Semana 4 sin cupo (no existe); semana 5 aún no publicada pero usable en la serie.
        cupo(martes.plusWeeks(4), "11:30", false);
        repos.citas.guardar(Cita.nueva(sinVerificarHabilitadoOtro(), tomado, CanalReserva.TENS, 2, true, null, ahora));

        AgendamientoService.PrevisualizacionSerie previa = servicio.previsualizarSerie(profesional.getId(), primero.getId(), 5);
        assertThat(previa.conCupo()).extracting(AgendamientoService.FechaSerie::fecha)
                .containsExactly(martes, martes.plusWeeks(1), martes.plusWeeks(4));
        assertThat(previa.sinCupo()).extracting(AgendamientoService.FechaSerie::motivo)
                .containsExactly("Horario ya reservado", "No hay horario ese día");

        List<Cita> creadas = servicio.reservarSerie(habilitado.getId(), profesional.getId(), primero.getId(), 5,
                List.of(martes, martes.plusWeeks(4)));
        assertThat(creadas).hasSize(2).allMatch(c -> c.getSerie() != null && c.getEstado() == EstadoCita.RESERVADA);
        assertThat(repos.citas.series).singleElement().satisfies(s -> assertThat(s.getCantidadSesiones()).isEqualTo(5));
    }

    @Test
    void serieFueraDeRangoSeRechaza() {
        Cupo primero = cupo(martes, "11:30", false);
        assertThatThrownBy(() -> servicio.previsualizarSerie(profesional.getId(), primero.getId(), 31)).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> servicio.previsualizarSerie(profesional.getId(), primero.getId(), 1)).isInstanceOf(ErrorNegocio.class);
    }

    private Paciente sinVerificarHabilitadoOtro() {
        Paciente otro = paciente("15432109-8", LocalDate.of(1985, 1, 1), true, ahora);
        return otro;
    }
}
