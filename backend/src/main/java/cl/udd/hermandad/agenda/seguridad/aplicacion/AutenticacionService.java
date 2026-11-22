package cl.udd.hermandad.agenda.seguridad.aplicacion;

import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import cl.udd.hermandad.agenda.compartido.UsuarioSesion;
import cl.udd.hermandad.agenda.seguridad.dominio.IUsuarioRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacionService {

    /** Hash para comparar aunque el usuario no exista (mismo tiempo de respuesta). */
    private final String hashFalso;

    private final IUsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public AutenticacionService(IUsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.hashFalso = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public UsuarioSesion ingresar(String nombreUsuario, String contrasena, PerfilUsuario perfilSolicitado) {
        Usuario u = usuarios.findByNombreUsuario(nombreUsuario == null ? "" : nombreUsuario.trim()).orElse(null);
        String hash = u == null ? hashFalso : u.getHashContrasena();
        boolean coincide = encoder.matches(contrasena == null ? "" : contrasena, hash);
        if (u == null || !coincide || !u.isActivo()) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.NO_AUTENTICADO, "CREDENCIALES",
                    "El usuario o la contraseña no son correctos.");
        }
        // El perfil sale de la cuenta (corrección P-00): ya no se elige al ingresar. Si un cliente antiguo lo envía,
        // se sigue validando que coincida.
        PerfilUsuario perfil = u.getPerfil().tipo();
        if (perfilSolicitado != null && perfil != perfilSolicitado) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.SIN_PERMISO, "PERFIL_NO_CORRESPONDE",
                    "Este usuario no tiene perfil de " + perfilSolicitado.nombre() + ".");
        }
        Policlinico base = u.getPoliclinico();
        return new UsuarioSesion(u.getId(), u.getNombreUsuario(), u.getNombreCompleto(), perfil,
                base == null ? null : base.getId(), base == null ? null : base.getNombre());
    }
}
