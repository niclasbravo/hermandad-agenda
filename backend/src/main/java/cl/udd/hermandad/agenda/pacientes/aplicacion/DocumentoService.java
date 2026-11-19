package cl.udd.hermandad.agenda.pacientes.aplicacion;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.pacientes.dominio.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

/** RF-41. Valida el tipo real por los primeros bytes y el tamaño (máximo 5 MB). */
@Service
@Transactional
public class DocumentoService {

    public static final int MAXIMO_BYTES = 5 * 1024 * 1024;

    public record Archivo(DocumentoAdjunto documento, InputStream contenido) {
    }

    private final IPacienteRepository pacientes;
    private final IAlmacenArchivos almacen;
    private final Reloj reloj;

    public DocumentoService(IPacienteRepository pacientes, IAlmacenArchivos almacen, Reloj reloj) {
        this.pacientes = pacientes;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    public DocumentoAdjunto guardar(Integer idPaciente, TipoAdjunto tipo, String nombreArchivo, byte[] contenido) {
        if (tipo == null) {
            throw ErrorNegocio.validacion("tipo", "Indica qué certificado es.");
        }
        if (contenido == null || contenido.length == 0) {
            throw ErrorNegocio.validacion("archivo", "El archivo está vacío.");
        }
        if (contenido.length > MAXIMO_BYTES) {
            throw ErrorNegocio.validacion("archivo", "El archivo pesa más de 5 MB. Sube uno más liviano.");
        }
        String mime = tipoReal(contenido);
        if (mime == null) {
            throw ErrorNegocio.validacion("archivo", "Solo se aceptan fotos JPG o PNG y documentos PDF.");
        }
        Paciente p = pacientes.buscar(idPaciente).orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos a ese paciente."));
        String extension = switch (mime) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            default -> "jpg";
        };
        String ruta = almacen.guardar(contenido, extension);
        return pacientes.guardarDocumento(new DocumentoAdjunto(p, tipo, nombreArchivo, mime, contenido.length, ruta, reloj.ahora()));
    }

    @Transactional(readOnly = true)
    public Archivo abrir(Integer idDocumento) {
        DocumentoAdjunto d = pacientes.buscarDocumento(idDocumento)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No encontramos ese documento."));
        return new Archivo(d, almacen.abrir(d.getRutaAlmacenamiento()));
    }

    /** Tipo MIME según los «magic bytes»; null si no es PDF, JPG ni PNG. */
    public static String tipoReal(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }
}
