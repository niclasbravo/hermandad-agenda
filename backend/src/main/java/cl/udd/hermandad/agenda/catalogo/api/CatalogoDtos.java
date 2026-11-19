package cl.udd.hermandad.agenda.catalogo.api;

import cl.udd.hermandad.agenda.catalogo.aplicacion.CatalogoService;
import cl.udd.hermandad.agenda.catalogo.dominio.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    public record BoxDto(Integer id, String nombre, boolean activo) {
        public static BoxDto de(Box b) {
            return new BoxDto(b.getId(), b.getNombre(), b.isActivo());
        }
    }

    public record PoliclinicoDto(Integer id, String nombre, String direccion, String comuna, boolean activo, List<BoxDto> boxes) {
        public static PoliclinicoDto de(Policlinico p) {
            return new PoliclinicoDto(p.getId(), p.getNombre(), p.getDireccion(), p.getComuna(), p.isActivo(),
                    p.boxesOrdenados().stream().map(BoxDto::de).toList());
        }
    }

    public record ProfesionDto(Integer id, String nombre, Integer duracionMin, Integer duracionAlumnoMin) {
        public static ProfesionDto de(Profesion p) {
            return new ProfesionDto(p.getId(), p.getNombre(), p.getDuracionMin(), p.getDuracionAlumnoMin());
        }
    }

    public record RefDto(Integer id, String nombre) {
    }

    public record CredencialDto(String tituloProfesional, String casaEstudios, String registroPrestador) {
    }

    public record ProfesionalDto(Integer id, String nombres, String apellidos, String nombreCompleto, ProfesionDto profesion,
                                 Integer duracionAtencion, boolean esAlumnoPractica, CredencialDto credencial,
                                 List<RefDto> policlinicos, Integer plazoPublicacionDias, boolean activo, String aviso) {
        public static ProfesionalDto de(Profesional p) {
            Credencial c = p.getCredencial();
            return new ProfesionalDto(p.getId(), p.getNombres(), p.getApellidos(), p.nombreCompleto(),
                    ProfesionDto.de(p.getProfesion()), p.duracionAtencion(), p.isEsAlumnoPractica(),
                    c == null ? new CredencialDto(null, null, null)
                            : new CredencialDto(c.getTituloProfesional(), c.getCasaEstudios(), c.getRegistroPrestador()),
                    p.policlinicosOrdenados().stream().map(x -> new RefDto(x.getId(), x.getNombre())).toList(),
                    p.getPlazoPublicacionDias(), p.isActivo(), CatalogoService.avisoDuracion(p).orElse(null));
        }
    }

    public record PoliclinicoRequest(@NotBlank(message = "Escribe el nombre.") @Size(max = 80) String nombre,
                                     @NotBlank(message = "Escribe la dirección.") @Size(max = 150) String direccion,
                                     @NotBlank(message = "Escribe la comuna.") @Size(max = 60) String comuna,
                                     Boolean activo, List<String> boxes) {
    }

    public record BoxRequest(@NotBlank(message = "Escribe el nombre del box.") @Size(max = 30) String nombre, Boolean activo) {
    }

    public record DuracionRequest(Integer duracionMin, Integer duracionAlumnoMin) {
    }

    public record ProfesionalRequest(@NotNull(message = "Elige la profesión.") Integer idProfesion,
                                     @NotBlank(message = "Escribe los nombres.") @Size(max = 80) String nombres,
                                     @NotBlank(message = "Escribe los apellidos.") @Size(max = 80) String apellidos,
                                     boolean esAlumnoPractica,
                                     @Size(max = 120) String tituloProfesional,
                                     @Size(max = 120) String casaEstudios,
                                     @Size(max = 40) String registroPrestador,
                                     @NotEmpty(message = "Elige al menos un policlínico donde atiende.") List<Integer> idsPoliclinico) {
        public CatalogoService.DatosProfesional datos() {
            return new CatalogoService.DatosProfesional(idProfesion, nombres, apellidos, esAlumnoPractica,
                    tituloProfesional, casaEstudios, registroPrestador, idsPoliclinico);
        }
    }

    public record PublicacionRequest(@NotNull(message = "Indica el plazo en días.") Integer plazoPublicacionDias) {
    }
}
