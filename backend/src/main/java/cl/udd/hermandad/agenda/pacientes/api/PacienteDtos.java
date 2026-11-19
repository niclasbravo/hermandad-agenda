package cl.udd.hermandad.agenda.pacientes.api;

import cl.udd.hermandad.agenda.compartido.Textos;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.dominio.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PacienteDtos {

    private PacienteDtos() {
    }

    public record AdultoRequest(TipoDocumento tipoDocumento, @Size(max = 20) String numeroDocumento,
                                @Size(max = 120) String nombreCompleto, @Size(max = 20) String telefono,
                                @Size(max = 40) String relacion) {
        DatosPaciente.DatosAdulto datos() {
            return new DatosPaciente.DatosAdulto(tipoDocumento, numeroDocumento, nombreCompleto, telefono, relacion);
        }
    }

    public record PacienteRequest(TipoDocumento tipoDocumento,
                                  @NotBlank(message = "Escribe el número de documento.") @Size(max = 20) String numeroDocumento,
                                  @NotBlank(message = "Escribe los nombres.") @Size(max = 80) String nombres,
                                  @NotBlank(message = "Escribe los apellidos.") @Size(max = 80) String apellidos,
                                  @NotNull(message = "Escribe la fecha de nacimiento.") LocalDate fechaNacimiento,
                                  @NotBlank(message = "Escribe el número de celular.") @Size(max = 20) String telefonoCelular,
                                  @Size(max = 120) String correo,
                                  @NotBlank(message = "Escribe la dirección.") @Size(max = 150) String direccion,
                                  @NotBlank(message = "Elige el género.") @Size(max = 30) String genero,
                                  @NotBlank(message = "Escribe la nacionalidad.") @Size(max = 60) String nacionalidad,
                                  @Valid AdultoRequest adulto) {
        public DatosPaciente datos() {
            return new DatosPaciente(tipoDocumento == null ? TipoDocumento.RUT : tipoDocumento, numeroDocumento, nombres,
                    apellidos, fechaNacimiento, telefonoCelular, correo, direccion, genero, nacionalidad,
                    adulto == null ? null : adulto.datos());
        }
    }

    public record VerificacionRequest(LocalDate fechaVerificacion,
                                      @NotBlank(message = "Escribe el CESFAM de inscripción.") @Size(max = 100) String cesfamInscripcion,
                                      boolean fonasaVigente, boolean inscritoCesfam,
                                      @Size(max = 300) String observacion, List<Integer> idsDocumento) {
    }

    public record AdultoDto(Integer id, TipoDocumento tipoDocumento, String numeroDocumento, String documentoFormateado,
                            String nombreCompleto, String telefono, String telefonoFormateado, String relacion) {
    }

    public record DocumentoDto(Integer id, TipoAdjunto tipo, String nombreArchivo, String tipoMime, Integer tamanoBytes,
                               LocalDateTime fechaCarga, Integer idVerificacion) {
        public static DocumentoDto de(DocumentoAdjunto d) {
            return new DocumentoDto(d.getId(), d.getTipo(), d.getNombreArchivo(), d.getTipoMime(), d.getTamanoBytes(),
                    d.getFechaCarga(), d.getVerificacion() == null ? null : d.getVerificacion().getId());
        }
    }

    public record VerificacionDto(Integer id, LocalDate fechaVerificacion, String cesfamInscripcion, boolean fonasaVigente,
                                  boolean inscritoCesfam, ResultadoVerificacion resultado, String observacion,
                                  String registradaPor, String perfilRegistro, String descripcion) {
        public static VerificacionDto de(VerificacionRequisitos v) {
            String quien = v.getUsuario().getNombreCompleto();
            String perfil = v.getUsuario().getPerfil().getNombre();
            String descripcion = v.getResultado() == ResultadoVerificacion.HABILITADO
                    ? "FONASA y " + v.getCesfamInscripcion() + " verificados el " + Textos.fechaCorta(v.getFechaVerificacion())
                      + " por " + quien + " (" + perfil + ")"
                    : "No cumple los requisitos (revisado el " + Textos.fechaCorta(v.getFechaVerificacion()) + " por " + quien + ")";
            return new VerificacionDto(v.getId(), v.getFechaVerificacion(), v.getCesfamInscripcion(), v.isFonasaVigente(),
                    v.isInscritoCesfam(), v.getResultado(), v.getObservacion(), quien, perfil, descripcion);
        }
    }

    public record PacienteDto(Integer id, TipoDocumento tipoDocumento, String numeroDocumento, String documentoFormateado,
                              String nombres, String apellidos, String nombreCompleto, String iniciales,
                              LocalDate fechaNacimiento, int edad, boolean esMenor, String telefonoCelular,
                              String telefonoFormateado, String correo, String direccion, String genero, String nacionalidad,
                              AdultoDto adultoResponsable, CanalRegistro canalRegistro, LocalDateTime fechaAvisoMensajes,
                              boolean habilitado, String estadoHabilitacion, VerificacionDto verificacionVigente) {
        public static PacienteDto de(Paciente p, LocalDate hoy) {
            AdultoResponsable a = p.getAdultoResponsable();
            AdultoDto adulto = a == null ? null : new AdultoDto(a.getId(), a.getDocumento().getTipo(),
                    a.getDocumento().getNumero(), a.getDocumento().formatear(), a.getNombreCompleto(), a.getTelefono(),
                    Telefono.formatear(a.getTelefono()), p.getRelacionAdulto());
            var vigente = p.verificacionVigente();
            String estado = vigente.map(v -> v.getResultado().name()).orElse("SIN_VERIFICACION");
            return new PacienteDto(p.getId(), p.getDocumento().getTipo(), p.getDocumento().getNumero(),
                    p.getDocumento().formatear(), p.getNombres(), p.getApellidos(), p.nombreCompleto(), p.iniciales(),
                    p.getFechaNacimiento(), p.edad(hoy), p.esMenor(hoy), p.getTelefonoCelular(),
                    Telefono.formatear(p.getTelefonoCelular()), p.getCorreo(), p.getDireccion(), p.getGenero(),
                    p.getNacionalidad(), adulto, p.getCanalRegistro(), p.getFechaAvisoMensajes(), p.estaHabilitado(),
                    estado, vigente.map(VerificacionDto::de).orElse(null));
        }
    }
}
