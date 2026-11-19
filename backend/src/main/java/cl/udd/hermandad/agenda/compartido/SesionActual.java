package cl.udd.hermandad.agenda.compartido;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SesionActual {

    private SesionActual() {
    }

    public static Optional<UsuarioSesion> opcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public static UsuarioSesion usuario() {
        return opcional().orElseThrow(() -> new ErrorNegocio(ErrorNegocio.Tipo.NO_AUTENTICADO, "SIN_SESION",
                "Tu sesión terminó. Vuelve a ingresar."));
    }

    /**
     * Policlínico que se usa para filtrar: la TENS/TONS siempre ve su policlínico base;
     * la Administración elige.
     */
    public static Integer policlinicoFiltrado(Integer solicitado) {
        UsuarioSesion u = usuario();
        if (u.esTens()) {
            return u.idPoliclinicoBase();
        }
        return solicitado;
    }
}
