package cl.udd.hermandad.agenda.seguridad.api;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.compartido.UsuarioSesion;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AutenticacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record IngresoRequest(@NotBlank(message = "Escribe tu usuario.") String usuario,
                                 @NotBlank(message = "Escribe tu contraseña.") String contrasena,
                                 @NotNull(message = "Elige el perfil.") PerfilUsuario perfil) {
    }

    public record YoResponse(Integer idUsuario, String usuario, String nombre, PerfilUsuario perfil,
                             Integer idPoliclinicoBase, String policlinicoBase) {
        static YoResponse de(UsuarioSesion u) {
            return new YoResponse(u.idUsuario(), u.nombreUsuario(), u.nombreCompleto(), u.perfil(),
                    u.idPoliclinicoBase(), u.policlinicoBase());
        }
    }

    private final AutenticacionService autenticacion;
    private final SecurityContextRepository contextos;

    public AuthController(AutenticacionService autenticacion, SecurityContextRepository contextos) {
        this.autenticacion = autenticacion;
        this.contextos = contextos;
    }

    @PostMapping("/ingresar")
    public YoResponse ingresar(@Valid @RequestBody IngresoRequest req, HttpServletRequest request,
                               HttpServletResponse response) {
        UsuarioSesion usuario = autenticacion.ingresar(req.usuario(), req.contrasena(), req.perfil());
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        request.getSession(true);
        var token = new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority(usuario.perfil().rol())));
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(token);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, request, response);
        return YoResponse.de(usuario);
    }

    @PostMapping("/salir")
    public ResponseEntity<Void> salir(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/yo")
    public YoResponse yo() {
        return YoResponse.de(SesionActual.opcional().orElseThrow(() ->
                new ErrorNegocio(ErrorNegocio.Tipo.NO_AUTENTICADO, "SIN_SESION", "Tu sesión terminó. Vuelve a ingresar.")));
    }

    /** Fuerza la entrega de la cookie XSRF-TOKEN a la SPA. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("cabecera", token.getHeaderName());
    }
}
