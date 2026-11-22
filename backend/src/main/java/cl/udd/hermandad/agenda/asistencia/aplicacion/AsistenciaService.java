package cl.udd.hermandad.agenda.asistencia.aplicacion;

import cl.udd.hermandad.agenda.agenda.aplicacion.AgendaService;
import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.asistencia.dominio.AgendaDelDia;
import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.citas.dominio.ICitaRepository;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.compartido.UsuarioSesion;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** CU-06: agenda del día y registro de asistencia (RF-18, RF-22). */
@Service
@Transactional
public class AsistenciaService {

    private final ICitaRepository citas;
    private final AgendaService agenda;
    private final AuditoriaService auditoria;
    private final Reloj reloj;

    public AsistenciaService(ICitaRepository citas, AgendaService agenda, AuditoriaService auditoria, Reloj reloj) {
        this.citas = citas;
        this.agenda = agenda;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public AgendaDelDia agendaDelDia(Integer idPoliclinico, LocalDate fecha) {
        return new AgendaDelDia(fecha == null ? reloj.hoy() : fecha, citas.buscarDelDia(idPoliclinico, fecha == null ? reloj.hoy() : fecha));
    }

    /** Cupos del día del policlínico (para las vistas por profesional y por box). */
    @Transactional(readOnly = true)
    public List<CupoVista> cuposDelDia(Integer idPoliclinico, LocalDate fecha) {
        LocalDate f = fecha == null ? reloj.hoy() : fecha;
        return agenda.agendaSemanal(null, idPoliclinico, f).stream().filter(c -> c.fecha().equals(f)).toList();
    }

    /** RF-22: «Asistió» o «No asistió», una sola vez. Se refleja en el historial y los reportes sin traspasos. */
    public Cita registrarAsistencia(Long idCita, boolean asistio) {
        UsuarioSesion u = SesionActual.usuario();
        Cita cita = citas.buscarParaModificar(idCita).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos esa cita."));
        Integer pol = cita.getCupo().getBloque().getBox().getPoliclinico().getId();
        if (u.esTens() && !pol.equals(u.idPoliclinicoBase())) {
            throw ErrorNegocio.sinPermiso("Esta cita es de otro policlínico.");
        }
        cita.registrarAsistencia(asistio, u.idUsuario(), reloj.ahora());
        citas.guardar(cita);
        auditoria.registrar(AccionAuditoria.MODIFICAR, "cita", cita.getId(), asistio ? "Asistió" : "No asistió");
        return cita;
    }
}
