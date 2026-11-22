package cl.udd.hermandad.agenda.compartido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ErrorNegocio.class)
    public ResponseEntity<RespuestaError> negocio(ErrorNegocio e) {
        return respuesta(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException e) {
        Map<String, Object> campos = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new RespuestaError("VALIDACION",
                "Revisa los datos marcados.", Map.of("campos", campos)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<RespuestaError> malFormado(Exception e) {
        return ResponseEntity.badRequest().body(new RespuestaError("VALIDACION",
                "Los datos enviados no son válidos. Revisa el formulario.", Map.of()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<RespuestaError> faltaArchivo(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(new RespuestaError("VALIDACION",
                "Falta el archivo. Elige una foto o un PDF.", Map.of("campos", Map.of(e.getRequestPartName(), "Falta el archivo."))));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RespuestaError> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new RespuestaError("METODO_NO_PERMITIDO",
                "Esta operación no está disponible.", Map.of()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RespuestaError> tipoNoSoportado(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new RespuestaError("TIPO_NO_SOPORTADO",
                "Los datos enviados no tienen el formato esperado.", Map.of()));
    }

    /** El cliente pidió un formato que no se ofrece: se responde sin cuerpo (no hay formato aceptable para él). */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> noAceptable(HttpMediaTypeNotAcceptableException e) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RespuestaError> archivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(new RespuestaError("ARCHIVO_GRANDE",
                "El archivo pesa más de 5 MB. Sube uno más liviano.", Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RespuestaError> sinPermiso(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new RespuestaError("SIN_PERMISO",
                "Tu perfil no tiene permiso para hacer esto.", Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespuestaError> noExiste(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new RespuestaError("NO_ENCONTRADO",
                "No encontramos lo que buscabas.", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> general(Exception e) {
        var traducido = TraductorErroresBaseDatos.traducir(e);
        if (traducido.isPresent()) {
            return respuesta(traducido.get());
        }
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RespuestaError("ERROR_INTERNO",
                "Ocurrió un problema. Inténtalo de nuevo en un momento.", Map.of()));
    }

    public static HttpStatus estado(ErrorNegocio.Tipo tipo) {
        return switch (tipo) {
            case VALIDACION -> HttpStatus.BAD_REQUEST;
            case NO_AUTENTICADO -> HttpStatus.UNAUTHORIZED;
            case SIN_PERMISO -> HttpStatus.FORBIDDEN;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO -> HttpStatus.CONFLICT;
            case REGLA_NEGOCIO -> HttpStatus.UNPROCESSABLE_ENTITY;
            case DEMASIADOS_INTENTOS -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }

    private ResponseEntity<RespuestaError> respuesta(ErrorNegocio e) {
        return ResponseEntity.status(estado(e.getTipo()))
                .body(new RespuestaError(e.getCodigo(), e.getMessage(), e.getDetalles()));
    }
}
