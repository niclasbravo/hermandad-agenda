package cl.udd.hermandad.agenda.agenda.aplicacion;

import cl.udd.hermandad.agenda.agenda.dominio.*;
import cl.udd.hermandad.agenda.catalogo.aplicacion.CatalogoService;
import cl.udd.hermandad.agenda.catalogo.dominio.Box;
import cl.udd.hermandad.agenda.catalogo.dominio.Profesional;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.compartido.Textos;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroCambiado;
import cl.udd.hermandad.agenda.seguridad.aplicacion.ParametroService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/** CU-02: bloques horarios, generación de cupos, bloqueos, publicación y cupos web. */
@Service
@Transactional
public class AgendaService {

    public record Previsualizacion(int cantidadPorDia, int duracion, List<LocalTime> horas, int dias, int totalCupos,
                                   String profesion, String mensaje) {
    }

    private final IBloqueRepository bloques;
    private final ICupoRepository cupos;
    private final CatalogoService catalogo;
    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final Reloj reloj;

    public AgendaService(IBloqueRepository bloques, ICupoRepository cupos, CatalogoService catalogo,
                         ParametroService parametros, AuditoriaService auditoria, Reloj reloj) {
        this.bloques = bloques;
        this.cupos = cupos;
        this.catalogo = catalogo;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // ---------------- Bloques (RF-04, RF-06) ----------------

    @Transactional(readOnly = true)
    public Previsualizacion previsualizar(DatosBloque d) {
        BloqueHorario b = construir(d, null);
        Integer duracion = b.getProfesional().duracionAtencion();
        List<LocalTime> horas = b.horasDelDia(duracion);
        LocalDate desde = maximo(reloj.hoy(), d.vigenciaDesde());
        int dias = b.fechas(desde, null).size();
        String lista = unirHoras(horas);
        String profesion = b.getProfesional().getProfesion().getNombre();
        String mensaje = "Se generarán " + horas.size() + " cupos de " + duracion + " minutos: " + lista
                + ". La duración la define la profesión (" + profesion + ", " + duracion + " min).";
        return new Previsualizacion(horas.size(), duracion, horas, dias, horas.size() * dias, profesion, mensaje);
    }

    /**
     * La vigencia se guarda desde hoy como mínimo: los cupos solo se generan desde hoy, y así un bloque nuevo no choca
     * con bloques que se cerraron en el pasado al modificarlos (D-10).
     */
    public BloqueHorario crearBloque(DatosBloque d) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        BloqueHorario b = construir(desdeHoy(d), idUsuario);
        Integer duracion = b.getProfesional().duracionAtencion();
        b.horasDelDia(duracion);
        verificarSuperposicion(b);
        b = bloques.guardar(b);
        List<Cupo> nuevos = b.generarCupos(duracion, maximo(reloj.hoy(), b.getVigenciaDesde()), null);
        marcarCuposWeb(nuevos, Set.of());
        cupos.guardarTodos(nuevos);
        auditoria.registrar(AccionAuditoria.CREAR, "bloque_horario", b.getId(), descripcion(b) + " · " + nuevos.size() + " cupos");
        return b;
    }

    /**
     * D-10. Si cambian el día, el horario o el box, el bloque actual se cierra (vigencia hasta ayer, o se desactiva si
     * no había empezado) y se crea uno nuevo desde hoy. Si solo cambia la vigencia, se ajusta el mismo bloque.
     * Los cupos futuros que sobran quedan como historial con un bloqueo «Bloque modificado».
     * Si alguno tiene una cita vigente, responde 409 con las reservas afectadas.
     */
    public BloqueHorario modificarBloque(Integer idBloque, DatosBloque d) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        LocalDate hoy = reloj.hoy();
        BloqueHorario actual = bloque(idBloque);
        if (!actual.isActivo()) {
            throw ErrorNegocio.regla("BLOQUE_INACTIVO", "Este bloque está desactivado y no se puede modificar.");
        }
        boolean cambiaForma = !actual.getProfesional().getId().equals(d.idProfesional())
                || !actual.getBox().getId().equals(d.idBox())
                || !actual.getDiaSemana().equals(d.diaSemana())
                || !actual.getHoraInicio().equals(d.horaInicio())
                || !actual.getHoraTermino().equals(d.horaTermino());

        if (!cambiaForma) {
            BloqueHorario prueba = construir(d, idUsuario);
            List<Cupo> futuros = cupos.delBloque(idBloque, hoy, null);
            List<Cupo> sobrantes = futuros.stream()
                    .filter(c -> c.getFecha().isBefore(d.vigenciaDesde()) || c.getFecha().isAfter(d.vigenciaHasta()))
                    .toList();
            exigirSinAfectadas(sobrantes);
            actual.cambiarVigencia(d.vigenciaDesde(), d.vigenciaHasta());
            verificarSuperposicion(actual);
            bloques.guardar(actual);
            marcarSobrantes(sobrantes, idUsuario);
            desbloquearVigentes(futuros, d.vigenciaDesde(), d.vigenciaHasta());
            Integer duracion = actual.getProfesional().duracionAtencion();
            if (duracion != null) {
                Set<String> existentes = cupos.delBloque(idBloque, null, null).stream()
                        .map(c -> c.getFecha() + "|" + c.getHoraInicio()).collect(Collectors.toSet());
                List<Cupo> faltantes = actual.generarCupos(duracion, maximo(hoy, d.vigenciaDesde()), null).stream()
                        .filter(c -> !existentes.contains(c.getFecha() + "|" + c.getHoraInicio())).toList();
                marcarCuposWeb(faltantes, Set.of());
                cupos.guardarTodos(faltantes);
            }
            auditoria.registrar(AccionAuditoria.MODIFICAR, "bloque_horario", actual.getId(),
                    "Vigencia " + Textos.fechaCorta(d.vigenciaDesde()) + " a " + Textos.fechaCorta(d.vigenciaHasta()));
            return actual;
        }

        BloqueHorario nuevo = construir(new DatosBloque(d.idProfesional(), d.idBox(), d.diaSemana(), d.horaInicio(),
                d.horaTermino(), maximo(hoy, d.vigenciaDesde()), d.vigenciaHasta()), idUsuario);
        Integer duracion = nuevo.getProfesional().duracionAtencion();
        nuevo.horasDelDia(duracion);
        List<Cupo> sobrantes = cupos.delBloque(idBloque, hoy, null);
        exigirSinAfectadas(sobrantes);
        cerrar(actual, hoy);
        bloques.guardar(actual);
        verificarSuperposicion(nuevo);
        nuevo = bloques.guardar(nuevo);
        marcarSobrantes(sobrantes, idUsuario);
        List<Cupo> generados = nuevo.generarCupos(duracion);
        marcarCuposWeb(generados, Set.of());
        cupos.guardarTodos(generados);
        auditoria.registrar(AccionAuditoria.MODIFICAR, "bloque_horario", actual.getId(),
                "Reemplazado por el bloque " + nuevo.getId() + ": " + descripcion(nuevo));
        auditoria.registrar(AccionAuditoria.CREAR, "bloque_horario", nuevo.getId(), descripcion(nuevo));
        return nuevo;
    }

    public BloqueHorario desactivarBloque(Integer idBloque) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        BloqueHorario actual = bloque(idBloque);
        if (!actual.isActivo()) {
            throw ErrorNegocio.regla("BLOQUE_INACTIVO", "Este bloque ya está desactivado.");
        }
        List<Cupo> sobrantes = cupos.delBloque(idBloque, reloj.hoy(), null);
        exigirSinAfectadas(sobrantes);
        actual.desactivar();
        bloques.guardar(actual);
        marcarSobrantes(sobrantes, idUsuario);
        auditoria.registrar(AccionAuditoria.MODIFICAR, "bloque_horario", actual.getId(), "Desactivado");
        return actual;
    }

    @Transactional(readOnly = true)
    public BloqueHorario bloque(Integer idBloque) {
        return bloques.buscar(idBloque).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese bloque horario."));
    }

    @Transactional(readOnly = true)
    public List<BloqueHorario> bloquesDelProfesional(Integer idProfesional) {
        LocalDate hoy = reloj.hoy();
        // Un bloque cerrado al modificarlo (vigencia hasta ayer) es historial: no se muestra para modificar ni desactivar.
        return bloques.activosDelProfesional(idProfesional).stream()
                .filter(b -> !b.getVigenciaHasta().isBefore(hoy)).toList();
    }

    @Transactional(readOnly = true)
    public List<CitaAfectada> afectadasAlDesactivar(Integer idBloque) {
        List<Cupo> futuros = cupos.delBloque(idBloque, reloj.hoy(), null);
        return cupos.citasVigentes(futuros.stream().map(Cupo::getId).toList());
    }

    // ---------------- Bloqueos (RF-07, D-12) ----------------

    public List<Cupo> bloquearCupos(List<Long> idsCupo, Integer idProfesional, LocalDate fecha, LocalTime desde,
                                    LocalTime hasta, String motivo) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        List<Cupo> objetivo;
        if (idsCupo != null && !idsCupo.isEmpty()) {
            objetivo = cupos.buscarVarios(idsCupo);
        } else {
            if (idProfesional == null || fecha == null || desde == null || hasta == null || !hasta.isAfter(desde)) {
                throw ErrorNegocio.validacion("hasta", "Indica el profesional, la fecha y un rango de horas válido.");
            }
            objetivo = cupos.deLaSemana(idProfesional, null, null, fecha, fecha, reloj.hoy()).stream()
                    .filter(c -> c.getBloque().isActivo())
                    .filter(c -> c.getHoraInicio().isBefore(hasta) && desde.isBefore(c.getHoraTermino())).toList();
        }
        if (objetivo.isEmpty()) {
            throw ErrorNegocio.regla("SIN_CUPOS", "No hay cupos en ese horario para bloquear.");
        }
        List<CitaAfectada> conCita = cupos.citasVigentes(objetivo.stream().map(Cupo::getId).toList());
        if (!conCita.isEmpty()) {
            throw ErrorNegocio.conflicto("CUPO_CON_RESERVA",
                    "No se puede bloquear un horario que tiene una cita. Anula o reasigna la cita primero.",
                    Map.of("afectadas", conCita));
        }
        Bloqueo bloqueo = cupos.guardarBloqueo(new Bloqueo(idUsuario, motivo, reloj.ahora()));
        objetivo.forEach(c -> c.bloquear(bloqueo));
        cupos.guardar(objetivo.get(0));
        auditoria.registrar(AccionAuditoria.BLOQUEAR, "bloqueo", bloqueo.getId(),
                motivo + " · " + objetivo.size() + " cupos: " + objetivo.stream().map(c -> String.valueOf(c.getId()))
                        .collect(Collectors.joining(",")));
        return objetivo;
    }

    // ---------------- Vistas (RF-40) ----------------

    @Transactional(readOnly = true)
    public List<CupoVista> agendaSemanal(Integer idProfesional, Integer idPoliclinico, LocalDate desde) {
        LocalDate lunes = lunesDe(desde == null ? reloj.hoy() : desde);
        return vistas(cupos.deLaSemana(idProfesional, idPoliclinico, null, lunes, lunes.plusDays(6), reloj.hoy()));
    }

    @Transactional(readOnly = true)
    public List<CupoVista> agendaPorBox(Integer idBox, LocalDate desde) {
        LocalDate lunes = lunesDe(desde == null ? reloj.hoy() : desde);
        return vistas(cupos.deLaSemana(null, null, idBox, lunes, lunes.plusDays(6), reloj.hoy()));
    }

    @Transactional(readOnly = true)
    public List<CupoVista> vistas(List<Cupo> lista) {
        List<Long> ids = lista.stream().map(Cupo::getId).toList();
        Set<Long> reservados = cupos.conCitaVigente(ids);
        Map<Long, String> pacientes = cupos.pacientesDeCupos(ids);
        LocalDateTime ahora = reloj.ahora();
        return lista.stream().map(c -> vista(c, reservados.contains(c.getId()), pacientes.get(c.getId()), ahora)).toList();
    }

    public static CupoVista vista(Cupo c, boolean reservado, String paciente, LocalDateTime ahora) {
        BloqueHorario b = c.getBloque();
        Profesional p = b.getProfesional();
        Box x = b.getBox();
        return new CupoVista(c.getId(), c.getFecha(), c.getHoraInicio(), c.getHoraTermino(), c.duracionMinutos(),
                c.estado(ahora, reservado), c.isHabilitadoWeb(), c.getBloqueo() == null ? null : c.getBloqueo().getMotivo(),
                p.getId(), p.nombreCompleto(), p.getProfesion().getId(), p.getProfesion().getNombre(),
                x.getId(), x.getNombre(), x.getPoliclinico().getId(), x.getPoliclinico().getNombre(),
                x.getPoliclinico().getDireccion(), paciente);
    }

    // ---------------- Cupos web (RF-37) ----------------

    /** Si cambia el %, se recalculan los cupos futuros no reservados. */
    @EventListener
    public void alCambiarParametro(ParametroCambiado evento) {
        if (ParametroService.PORCENTAJE_CUPOS_WEB.equals(evento.clave())) {
            recalcularCuposWeb();
        }
    }

    public int recalcularCuposWeb() {
        List<Cupo> futuros = cupos.futurosDeBloquesActivos(reloj.hoy());
        Set<Long> reservados = cupos.conCitaVigente(futuros.stream().map(Cupo::getId).toList());
        marcarCuposWeb(futuros, reservados);
        return futuros.size();
    }

    /** Marca habilitado_web en ceil(n × %/100) cupos de cada bloque y fecha, repartidos de forma pareja. */
    void marcarCuposWeb(List<Cupo> lista, Set<Long> reservados) {
        ReglaReservaWeb regla = new ReglaReservaWeb(parametros.porcentajeCuposWeb().orElse(null), null);
        Map<String, List<Cupo>> grupos = new LinkedHashMap<>();
        for (Cupo c : lista) {
            grupos.computeIfAbsent(System.identityHashCode(c.getBloque()) + "|" + c.getFecha(), k -> new ArrayList<>()).add(c);
        }
        for (List<Cupo> grupo : grupos.values()) {
            grupo.sort(Comparator.comparing(Cupo::getHoraInicio));
            Set<Integer> indices = new HashSet<>(regla.indicesWeb(grupo.size()));
            for (int i = 0; i < grupo.size(); i++) {
                Cupo c = grupo.get(i);
                if (c.getId() == null || !reservados.contains(c.getId())) {
                    c.habilitarWeb(indices.contains(i));
                }
            }
        }
    }

    // ---------------- Apoyo ----------------

    private BloqueHorario construir(DatosBloque d, Integer idUsuario) {
        if (d.idProfesional() == null) {
            throw ErrorNegocio.validacion("idProfesional", "Elige el profesional.");
        }
        if (d.idBox() == null) {
            throw ErrorNegocio.validacion("idBox", "Elige el box.");
        }
        Profesional p = catalogo.profesional(d.idProfesional());
        if (!p.isActivo()) {
            throw ErrorNegocio.regla("PROFESIONAL_INACTIVO", "Este profesional está desactivado.");
        }
        Box box = catalogo.box(d.idBox());
        return new BloqueHorario(p, box, d.diaSemana() == null ? 0 : d.diaSemana(), d.horaInicio(), d.horaTermino(),
                d.vigenciaDesde(), d.vigenciaHasta(), idUsuario, reloj.ahora());
    }

    private void verificarSuperposicion(BloqueHorario b) {
        List<BloqueHorario> otros = bloques.buscarSuperpuestos(b);
        if (!otros.isEmpty()) {
            BloqueHorario o = otros.get(0);
            String mismo = o.getProfesional().getId().equals(b.getProfesional().getId()) ? "del mismo profesional" : "del mismo box";
            throw ErrorNegocio.conflicto("BLOQUE_SUPERPUESTO",
                    "Este horario se cruza con un bloque " + mismo + ": " + o.getProfesional().nombreCompleto() + ", "
                            + o.getBox().getPoliclinico().getNombre() + " " + o.getBox().getNombre() + ", "
                            + DIAS[o.getDiaSemana() - 1] + " de " + Textos.hora2(o.getHoraInicio()) + " a "
                            + Textos.hora2(o.getHoraTermino()) + ", vigente del " + Textos.fechaCorta(o.getVigenciaDesde())
                            + " al " + Textos.fechaCorta(o.getVigenciaHasta()) + ". Elige otro horario o box.",
                    Map.of("idBloque", o.getId()));
        }
    }

    private static final String[] DIAS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};

    private DatosBloque desdeHoy(DatosBloque d) {
        LocalDate hoy = reloj.hoy();
        if (d.vigenciaHasta() != null && d.vigenciaHasta().isBefore(hoy)) {
            throw ErrorNegocio.validacion("vigenciaHasta", "La vigencia debe terminar hoy o después.");
        }
        LocalDate desde = d.vigenciaDesde() == null ? null : maximo(hoy, d.vigenciaDesde());
        return new DatosBloque(d.idProfesional(), d.idBox(), d.diaSemana(), d.horaInicio(), d.horaTermino(), desde,
                d.vigenciaHasta());
    }

    /** Vuelve a ofrecer los cupos que un recorte de vigencia había bloqueado y que quedan otra vez dentro de ella. */
    private void desbloquearVigentes(List<Cupo> futuros, LocalDate desde, LocalDate hasta) {
        List<Cupo> candidatos = futuros.stream()
                .filter(c -> c.getBloqueo() != null && Bloqueo.MOTIVO_BLOQUE_MODIFICADO.equals(c.getBloqueo().getMotivo()))
                .filter(c -> !c.getFecha().isBefore(desde) && !c.getFecha().isAfter(hasta))
                .toList();
        if (candidatos.isEmpty()) {
            return;
        }
        Set<Long> conCita = cupos.conCitaVigente(candidatos.stream().map(Cupo::getId).toList());
        List<Cupo> libres = candidatos.stream().filter(c -> !conCita.contains(c.getId())).toList();
        libres.forEach(Cupo::desbloquear);
        cupos.guardarTodos(libres);
    }

    private void exigirSinAfectadas(List<Cupo> sobrantes) {
        List<CitaAfectada> afectadas = cupos.citasVigentes(sobrantes.stream().map(Cupo::getId).toList());
        if (!afectadas.isEmpty()) {
            throw ErrorNegocio.conflicto("RESERVAS_AFECTADAS",
                    "Este cambio afecta " + afectadas.size() + (afectadas.size() == 1 ? " reserva" : " reservas")
                            + ". Reasígnalas o anúlalas con aviso al paciente antes de guardar.",
                    Map.of("afectadas", afectadas));
        }
    }

    private void marcarSobrantes(List<Cupo> sobrantes, Integer idUsuario) {
        List<Cupo> libres = sobrantes.stream().filter(c -> c.getBloqueo() == null).toList();
        if (libres.isEmpty()) {
            return;
        }
        Bloqueo bloqueo = cupos.guardarBloqueo(new Bloqueo(idUsuario, Bloqueo.MOTIVO_BLOQUE_MODIFICADO, reloj.ahora()));
        libres.forEach(c -> c.bloquear(bloqueo));
        cupos.guardar(libres.get(0));
    }

    private static void cerrar(BloqueHorario actual, LocalDate hoy) {
        if (actual.getVigenciaDesde().isBefore(hoy)) {
            LocalDate hasta = actual.getVigenciaHasta().isBefore(hoy) ? actual.getVigenciaHasta() : hoy.minusDays(1);
            actual.cambiarVigencia(actual.getVigenciaDesde(), hasta);
        } else {
            actual.desactivar();
        }
    }

    public static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(DayOfWeek.MONDAY);
    }

    private static LocalDate maximo(LocalDate a, LocalDate b) {
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    static String unirHoras(List<LocalTime> horas) {
        List<String> t = horas.stream().map(Textos::hora2).toList();
        if (t.isEmpty()) {
            return "ninguna";
        }
        if (t.size() == 1) {
            return t.get(0);
        }
        return String.join(", ", t.subList(0, t.size() - 1)) + " y " + t.get(t.size() - 1);
    }

    private static String descripcion(BloqueHorario b) {
        return b.getProfesional().nombreCompleto() + ", " + b.getBox().getPoliclinico().getNombre() + " " + b.getBox().getNombre()
                + ", día " + b.getDiaSemana() + " " + Textos.hora2(b.getHoraInicio()) + "-" + Textos.hora2(b.getHoraTermino())
                + ", " + Textos.fechaCorta(b.getVigenciaDesde()) + " a " + Textos.fechaCorta(b.getVigenciaHasta());
    }
}
