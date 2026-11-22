package cl.udd.hermandad.agenda.pacientes.aplicacion;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.pacientes.dominio.*;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import cl.udd.hermandad.agenda.seguridad.dominio.IUsuarioRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.Usuario;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** CU-03 y la parte de pacientes de CU-08. */
@Service
@Transactional
public class PacienteService {

    private final IPacienteRepository pacientes;
    private final IUsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Reloj reloj;

    public PacienteService(IPacienteRepository pacientes, IUsuarioRepository usuarios, AuditoriaService auditoria,
                           ApplicationEventPublisher eventos, Reloj reloj) {
        this.pacientes = pacientes;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** RF-09: busca por documento; el RUT puede venir con o sin puntos ni guion. */
    @Transactional(readOnly = true)
    public Optional<Paciente> buscar(String documento) {
        if (documento == null || documento.isBlank()) {
            throw ErrorNegocio.validacion("documento", "Escribe el RUT o el documento del paciente.");
        }
        String rut = DocumentoIdentidad.normalizar(TipoDocumento.RUT, documento);
        if (DocumentoIdentidad.rutValido(rut)) {
            Optional<Paciente> p = pacientes.buscarPorDocumento(TipoDocumento.RUT, rut);
            if (p.isPresent()) {
                return p.map(this::inicializar);
            }
        }
        String otro = DocumentoIdentidad.normalizar(TipoDocumento.PASAPORTE, documento);
        return pacientes.buscarPorNumero(otro).stream().findFirst().map(this::inicializar);
    }

    @Transactional(readOnly = true)
    public Paciente obtener(Integer id) {
        return pacientes.buscar(id).map(this::inicializar)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos a ese paciente."));
    }

    /** RF-10, RF-12, RF-36. */
    public Paciente registrar(DatosPaciente d, CanalRegistro canal) {
        Integer idUsuario = canal == CanalRegistro.TENS ? SesionActual.usuario().idUsuario() : null;
        DocumentoIdentidad documento = DocumentoIdentidad.de(d.tipoDocumento(), d.numeroDocumento());
        Optional<Paciente> existente = pacientes.buscarPorDocumento(documento.getTipo(), documento.getNumero());
        if (existente.isPresent()) {
            throw ErrorNegocio.conflicto("DOCUMENTO_DUPLICADO",
                    "Ya existe un paciente con el documento " + documento.formatear() + ".",
                    Map.of("idPaciente", existente.get().getId()));
        }
        LocalDate hoy = reloj.hoy();
        Paciente p = new Paciente(documento, canal, idUsuario, reloj.ahora());
        AdultoResponsable adulto = adulto(d.adulto(), canal == CanalRegistro.TENS);
        p.actualizarDatos(d.nombres(), d.apellidos(), d.fechaNacimiento(), d.telefonoCelular(), d.correo(), d.direccion(),
                d.genero(), d.nacionalidad(), adulto, adulto == null ? null : d.adulto().relacion(), hoy);
        p = pacientes.guardar(p);
        auditoria.registrarComo(idUsuario, AccionAuditoria.CREAR, "paciente", p.getId(),
                "Registro " + (canal == CanalRegistro.TENS ? "por la TENS/TONS" : "en línea"));
        return p;
    }

    /** RF-11: queda auditado; las confirmaciones siguientes van al número nuevo. */
    public Paciente actualizar(Integer id, DatosPaciente d) {
        Paciente p = obtener(id);
        String telefonoAnterior = p.getTelefonoCelular();
        AdultoResponsable adulto = adulto(d.adulto(), true);
        p.actualizarDatos(d.nombres(), d.apellidos(), d.fechaNacimiento(), d.telefonoCelular(), d.correo(), d.direccion(),
                d.genero(), d.nacionalidad(), adulto, adulto == null ? null : d.adulto().relacion(), reloj.hoy());
        pacientes.guardar(p);
        String detalle = "Datos actualizados" + (telefonoAnterior.equals(p.getTelefonoCelular()) ? "" : " (cambió el celular)");
        auditoria.registrar(AccionAuditoria.MODIFICAR, "paciente", p.getId(), detalle);
        return p;
    }

    /** RF-13. HABILITADO solo si ambas casillas están marcadas. */
    public VerificacionRequisitos registrarVerificacion(Integer idPaciente, LocalDate fecha, String cesfam, boolean fonasa,
                                                        boolean inscrito, String observacion, List<Integer> idsDocumento) {
        Paciente p = obtener(idPaciente);
        Usuario usuario = usuarios.findById(SesionActual.usuario().idUsuario()).orElseThrow();
        VerificacionRequisitos v = new VerificacionRequisitos(p, usuario, fecha == null ? reloj.hoy() : fecha, cesfam,
                fonasa, inscrito, observacion, reloj.ahora());
        v = pacientes.guardarVerificacion(v);
        p.agregarVerificacion(v);
        if (idsDocumento != null) {
            for (Integer idDoc : idsDocumento) {
                DocumentoAdjunto doc = pacientes.buscarDocumento(idDoc)
                        .filter(x -> x.getPaciente().getId().equals(idPaciente))
                        .orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese documento."));
                doc.ligarA(v);
                pacientes.guardarDocumento(doc);
            }
        }
        auditoria.registrar(AccionAuditoria.VERIFICAR, "verificacion_requisitos", v.getId(),
                "Paciente " + idPaciente + ": " + v.getResultado() + ", CESFAM " + v.getCesfamInscripcion());
        eventos.publishEvent(new VerificacionRegistrada(idPaciente, v.getId(), v.getResultado()));
        return v;
    }

    /** RF-35: devuelve el paciente solo si el documento existe y la fecha de nacimiento coincide. */
    @Transactional(readOnly = true)
    public Optional<Paciente> identificarEnLinea(DocumentoIdentidad documento, LocalDate fechaNacimiento) {
        return pacientes.buscarPorDocumento(documento.getTipo(), documento.getNumero())
                .filter(p -> p.getFechaNacimiento().equals(fechaNacimiento))
                .map(this::inicializar);
    }

    @Transactional(readOnly = true)
    public boolean existeDocumento(DocumentoIdentidad documento) {
        return pacientes.buscarPorDocumento(documento.getTipo(), documento.getNumero()).isPresent();
    }

    @Transactional(readOnly = true)
    public List<DocumentoAdjunto> documentos(Integer idPaciente) {
        return pacientes.documentosDe(idPaciente);
    }

    private AdultoResponsable adulto(DatosPaciente.DatosAdulto d, boolean actualizarSiExiste) {
        if (d == null || d.vacio()) {
            return null;
        }
        if (d.nombreCompleto() == null || d.nombreCompleto().isBlank()) {
            throw ErrorNegocio.validacion("adulto.nombreCompleto", "Escribe el nombre del adulto responsable.");
        }
        DocumentoIdentidad doc = documentoDelAdulto(d);
        Optional<AdultoResponsable> existente = pacientes.buscarAdulto(doc.getTipo(), doc.getNumero());
        if (existente.isPresent()) {
            AdultoResponsable a = existente.get();
            if (actualizarSiExiste) {
                a.actualizar(d.nombreCompleto(), d.telefono());
                pacientes.guardarAdulto(a);
            }
            return a;
        }
        return pacientes.guardarAdulto(new AdultoResponsable(doc, d.nombreCompleto(), d.telefono()));
    }

    /** El error se marca en el documento del adulto (adulto.numeroDocumento), no en el del paciente. */
    private static DocumentoIdentidad documentoDelAdulto(DatosPaciente.DatosAdulto d) {
        try {
            return DocumentoIdentidad.de(d.tipoDocumento(), d.numeroDocumento());
        } catch (ErrorNegocio e) {
            if (e.getTipo() != ErrorNegocio.Tipo.VALIDACION || !(e.getDetalles().get("campos") instanceof Map<?, ?> campos)) {
                throw e;
            }
            Map<String, Object> prefijados = new LinkedHashMap<>();
            campos.forEach((k, v) -> prefijados.put("adulto." + k, v));
            throw new ErrorNegocio(e.getTipo(), e.getCodigo(), e.getMessage(), Map.of("campos", prefijados));
        }
    }

    private Paciente inicializar(Paciente p) {
        p.getVerificaciones().size();
        return p;
    }
}
