package cl.udd.hermandad.agenda.citas.aplicacion;

import cl.udd.hermandad.agenda.agenda.aplicacion.CupoVista;
import cl.udd.hermandad.agenda.agenda.dominio.FiltroCupos;
import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.citas.dominio.EstadoCita;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.LimiteIntentos;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DocumentoService;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * CU-08: identificación en línea sin revelar si el documento existe (CA-RF35-1), registro en línea (RF-36),
 * reserva (RF-34) y certificados (RF-41). El token vive 30 minutos en memoria (Caffeine) y queda ligado al documento.
 */
@Service
public class ReservaEnLineaService {

    public enum EstadoIdentificacion { IDENTIFICADO, NUEVO }

    public record Identificacion(String token, EstadoIdentificacion estado, String documentoEnmascarado, String nombrePila) {
    }

    static final class Sesion {
        final DocumentoIdentidad documento;
        final LocalDate fechaNacimiento;
        volatile Integer idPaciente;
        final Set<Long> citas = Collections.synchronizedSet(new HashSet<>());

        Sesion(DocumentoIdentidad documento, LocalDate fechaNacimiento, Integer idPaciente) {
            this.documento = documento;
            this.fechaNacimiento = fechaNacimiento;
            this.idPaciente = idPaciente;
        }
    }

    public static final String MSJ_NO_PUDIMOS = "No pudimos completar la reserva en línea con estos datos. "
            + "Pide tu hora en tu policlínico o por teléfono.";

    private final Cache<String, Sesion> sesiones = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30)).maximumSize(20_000).build();
    private final SecureRandom azar = new SecureRandom();

    private final PacienteService pacientes;
    private final DocumentoService documentos;
    private final AgendamientoService agendamiento;
    private final LimiteIntentos limite;
    private final Reloj reloj;

    public ReservaEnLineaService(PacienteService pacientes, DocumentoService documentos, AgendamientoService agendamiento,
                                 LimiteIntentos limite, Reloj reloj) {
        this.pacientes = pacientes;
        this.documentos = documentos;
        this.agendamiento = agendamiento;
        this.limite = limite;
        this.reloj = reloj;
    }

    /** Documento inexistente o con otra fecha de nacimiento: misma respuesta (NUEVO). Cada fallo cuenta para el 429. */
    public Identificacion identificar(TipoDocumento tipo, String numero, LocalDate fechaNacimiento, String ip) {
        limite.verificar(ip);
        DocumentoIdentidad doc;
        try {
            doc = DocumentoIdentidad.de(tipo, numero);
        } catch (ErrorNegocio e) {
            limite.registrarFallido(ip);
            throw e;
        }
        if (fechaNacimiento == null || fechaNacimiento.isAfter(reloj.hoy())) {
            limite.registrarFallido(ip);
            throw ErrorNegocio.validacion("fechaNacimiento", "Escribe una fecha de nacimiento válida.");
        }
        Optional<Paciente> p = pacientes.identificarEnLinea(doc, fechaNacimiento);
        String token = nuevoToken();
        if (p.isPresent()) {
            sesiones.put(token, new Sesion(doc, fechaNacimiento, p.get().getId()));
            return new Identificacion(token, EstadoIdentificacion.IDENTIFICADO, doc.enmascarar(), p.get().nombrePila());
        }
        limite.registrarFallido(ip);
        sesiones.put(token, new Sesion(doc, fechaNacimiento, null));
        return new Identificacion(token, EstadoIdentificacion.NUEVO, doc.enmascarar(), null);
    }

    /** RF-36. El documento y la fecha de nacimiento vienen del token, no del formulario. */
    public Identificacion registrar(String token, DatosPaciente d, String ip) {
        Sesion s = sesion(token);
        if (s.idPaciente != null) {
            throw ErrorNegocio.regla("YA_IDENTIFICADO", "Ya te identificaste. Continúa con la elección de tu hora.");
        }
        if (pacientes.existeDocumento(s.documento)) {
            limite.registrarFallido(ip);
            throw ErrorNegocio.regla("NO_SE_PUDO_REGISTRAR", MSJ_NO_PUDIMOS);
        }
        DatosPaciente datos = new DatosPaciente(s.documento.getTipo(), s.documento.getNumero(), d.nombres(), d.apellidos(),
                s.fechaNacimiento, d.telefonoCelular(), d.correo(), d.direccion(), d.genero(), d.nacionalidad(), d.adulto());
        Paciente p;
        try {
            p = pacientes.registrar(datos, CanalRegistro.EN_LINEA);
        } catch (ErrorNegocio e) {
            if (e.getTipo() == ErrorNegocio.Tipo.CONFLICTO) {
                throw ErrorNegocio.regla("NO_SE_PUDO_REGISTRAR", MSJ_NO_PUDIMOS);
            }
            throw e;
        }
        s.idPaciente = p.getId();
        return new Identificacion(token, EstadoIdentificacion.IDENTIFICADO, s.documento.enmascarar(), p.nombrePila());
    }

    @Transactional(readOnly = true)
    public List<CupoVista> cupos(Integer idPoliclinico, Integer idProfesion, Integer idProfesional, LocalDate desde) {
        LocalDate d = desde == null || desde.isBefore(reloj.hoy()) ? reloj.hoy() : desde;
        return agendamiento.consultarCupos(new FiltroCupos(idPoliclinico, idProfesion, idProfesional, d, d.plusDays(60), true));
    }

    public Cita reservar(String token, Long idCupo) {
        Sesion s = sesion(token);
        if (s.idPaciente == null) {
            throw ErrorNegocio.regla("SIN_DATOS", "Primero completa tus datos de ingreso.");
        }
        Cita c = agendamiento.reservarEnLinea(s.idPaciente, idCupo);
        s.citas.add(c.getId());
        return c;
    }

    /** RF-41: solo para la cita de este token y mientras esté pendiente de verificación. */
    public DocumentoAdjunto subirCertificado(String token, Long idCita, TipoAdjunto tipo, String nombre, byte[] contenido) {
        Sesion s = sesion(token);
        if (!s.citas.contains(idCita)) {
            throw ErrorNegocio.noEncontrado("No encontramos esa reserva.");
        }
        Cita c = agendamiento.cita(idCita);
        if (c.getEstado() != EstadoCita.PENDIENTE_VERIFICACION) {
            throw ErrorNegocio.regla("NO_REQUIERE", "Esta reserva no necesita certificados.");
        }
        return documentos.guardar(s.idPaciente, tipo, nombre, contenido);
    }

    private Sesion sesion(String token) {
        Sesion s = token == null ? null : sesiones.getIfPresent(token);
        if (s == null) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.NO_AUTENTICADO, "TOKEN_VENCIDO",
                    "Pasó mucho tiempo desde que te identificaste. Vuelve a empezar, por favor.");
        }
        return s;
    }

    private String nuevoToken() {
        byte[] b = new byte[32];
        azar.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
