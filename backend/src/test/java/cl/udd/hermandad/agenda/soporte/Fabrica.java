package cl.udd.hermandad.agenda.soporte;

import cl.udd.hermandad.agenda.agenda.dominio.BloqueHorario;
import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.catalogo.dominio.*;
import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import cl.udd.hermandad.agenda.compartido.UsuarioSesion;
import cl.udd.hermandad.agenda.pacientes.dominio.*;
import cl.udd.hermandad.agenda.seguridad.dominio.Perfil;
import cl.udd.hermandad.agenda.seguridad.dominio.Usuario;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Arma entidades del dominio para pruebas unitarias (sin Spring ni base de datos). */
public final class Fabrica {

    private static final AtomicLong IDS = new AtomicLong(1000);

    private Fabrica() {
    }

    public static <T> T conId(T entidad, Object id) {
        ReflectionTestUtils.setField(entidad, "id", id);
        return entidad;
    }

    public static Policlinico policlinico() {
        return conId(new Policlinico("Casa Central", "San Isidro 218", "Santiago Centro"), 1);
    }

    public static Box box(Policlinico p, int id) {
        return conId(p.agregarBox("Box " + id), id);
    }

    public static Profesion profesion(String nombre, Integer duracion) {
        return conId(new Profesion(nombre, duracion), (int) IDS.incrementAndGet());
    }

    public static Profesional profesional(Profesion profesion, Policlinico p, boolean alumno) {
        return conId(new Profesional(profesion, "Paula", "Rojas", alumno, new Credencial(null, null, null), Set.of(p)),
                (int) IDS.incrementAndGet());
    }

    public static BloqueHorario bloque(Profesional prof, Box box, int dia, String ini, String fin, LocalDate desde, LocalDate hasta) {
        return conId(new BloqueHorario(prof, box, dia, LocalTime.parse(ini), LocalTime.parse(fin), desde, hasta, 1,
                LocalDateTime.of(2026, 1, 1, 0, 0)), (int) IDS.incrementAndGet());
    }

    public static Cupo cupo(BloqueHorario b, LocalDate fecha, String hora, int duracion) {
        LocalTime h = LocalTime.parse(hora);
        return conId(new Cupo(b, fecha, h, h.plusMinutes(duracion)), IDS.incrementAndGet());
    }

    /** Cupo de Medicina (20 min), plazo de publicación 7 días. */
    public static Cupo cupoMedicina(LocalDate fecha, String hora) {
        Policlinico p = policlinico();
        Profesional prof = profesional(profesion("Medicina", 20), p, false);
        BloqueHorario b = bloque(prof, box(p, 1), fecha.getDayOfWeek().getValue(), "09:00", "12:00",
                fecha.minusMonths(1), fecha.plusMonths(1));
        return cupo(b, fecha, hora, 20);
    }

    public static Usuario usuarioTens(Policlinico p) {
        return conId(new Usuario(new Perfil("TENS/TONS", null), p, "Carolina Muñoz", "c.munoz", "x"), 2);
    }

    public static Paciente paciente(String rut, LocalDate nacimiento, boolean habilitado, LocalDateTime ahora) {
        Paciente pa = new Paciente(DocumentoIdentidad.de(TipoDocumento.RUT, rut), CanalRegistro.TENS, 2, ahora);
        AdultoResponsable adulto = null;
        if (nacimiento.isAfter(ahora.toLocalDate().minusYears(18))) {
            adulto = new AdultoResponsable(DocumentoIdentidad.de(TipoDocumento.RUT, "16987654-1"), "Valentina Vega", "+56911112222");
        }
        pa.actualizarDatos("María", "González Pérez", nacimiento, "+56912345678", null, "Calle 1", "Femenino", "Chilena",
                adulto, adulto == null ? null : "madre", ahora.toLocalDate());
        conId(pa, (int) IDS.incrementAndGet());
        if (habilitado) {
            pa.agregarVerificacion(conId(new VerificacionRequisitos(pa, usuarioTens(policlinico()), ahora.toLocalDate(),
                    "CESFAM Norte", true, true, null, ahora), (int) IDS.incrementAndGet()));
        }
        return pa;
    }

    public static void iniciarSesion(PerfilUsuario perfil) {
        UsuarioSesion u = new UsuarioSesion(2, "c.munoz", "Carolina Muñoz", perfil, perfil == PerfilUsuario.TENS ? 1 : null,
                perfil == PerfilUsuario.TENS ? "Casa Central" : null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of(new SimpleGrantedAuthority(perfil.rol()))));
    }

    public static void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }
}
