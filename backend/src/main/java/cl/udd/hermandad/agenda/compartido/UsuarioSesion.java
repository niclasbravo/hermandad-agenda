package cl.udd.hermandad.agenda.compartido;

import java.io.Serializable;

/** Datos del usuario que inició sesión. Se guarda en la sesión del servidor. */
public record UsuarioSesion(Integer idUsuario, String nombreUsuario, String nombreCompleto, PerfilUsuario perfil,
                            Integer idPoliclinicoBase, String policlinicoBase) implements Serializable {

    public boolean esAdministracion() {
        return perfil == PerfilUsuario.ADMINISTRACION;
    }

    public boolean esTens() {
        return perfil == PerfilUsuario.TENS;
    }
}
