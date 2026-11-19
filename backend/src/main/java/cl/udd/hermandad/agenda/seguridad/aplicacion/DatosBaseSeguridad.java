package cl.udd.hermandad.agenda.seguridad.aplicacion;

import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import cl.udd.hermandad.agenda.seguridad.dominio.IPerfilRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.IUsuarioRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.Perfil;
import cl.udd.hermandad.agenda.seguridad.dominio.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asegura los dos perfiles y los parámetros iniciales. Si no hay usuarios y se definieron
 * ADMIN_INICIAL_USUARIO y ADMIN_INICIAL_CONTRASENA, crea la primera cuenta de Administración.
 */
@Component
@Order(1)
public class DatosBaseSeguridad implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosBaseSeguridad.class);

    private final IPerfilRepository perfiles;
    private final IUsuarioRepository usuarios;
    private final ParametroService parametros;
    private final PasswordEncoder encoder;
    private final String adminUsuario;
    private final String adminContrasena;

    public DatosBaseSeguridad(IPerfilRepository perfiles, IUsuarioRepository usuarios, ParametroService parametros,
                              PasswordEncoder encoder,
                              @Value("${ADMIN_INICIAL_USUARIO:}") String adminUsuario,
                              @Value("${ADMIN_INICIAL_CONTRASENA:}") String adminContrasena) {
        this.perfiles = perfiles;
        this.usuarios = usuarios;
        this.parametros = parametros;
        this.encoder = encoder;
        this.adminUsuario = adminUsuario;
        this.adminContrasena = adminContrasena;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        asegurarPerfiles();
        parametros.asegurarIniciales();
        if (usuarios.count() == 0 && !adminUsuario.isBlank() && !adminContrasena.isBlank()) {
            Perfil admin = perfiles.findByNombre(PerfilUsuario.ADMINISTRACION.nombre()).orElseThrow();
            usuarios.save(new Usuario(admin, null, "Administración", adminUsuario, encoder.encode(adminContrasena)));
            log.info("Se creó la cuenta inicial de Administración");
        }
    }

    public void asegurarPerfiles() {
        if (perfiles.findByNombre(PerfilUsuario.ADMINISTRACION.nombre()).isEmpty()) {
            perfiles.save(new Perfil(PerfilUsuario.ADMINISTRACION.nombre(),
                    "Configura policlínicos, profesionales, bloques, parámetros y ve los reportes."));
        }
        if (perfiles.findByNombre(PerfilUsuario.TENS.nombre()).isEmpty()) {
            perfiles.save(new Perfil(PerfilUsuario.TENS.nombre(),
                    "Agenda, confirma y registra la asistencia en su policlínico."));
        }
    }
}
