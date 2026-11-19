package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.agenda.aplicacion.AgendaService;
import cl.udd.hermandad.agenda.agenda.aplicacion.DatosBloque;
import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.UsuarioSesion;
import cl.udd.hermandad.agenda.pacientes.aplicacion.DatosPaciente;
import cl.udd.hermandad.agenda.pacientes.aplicacion.PacienteService;
import cl.udd.hermandad.agenda.pacientes.dominio.CanalRegistro;
import cl.udd.hermandad.agenda.pacientes.dominio.DocumentoIdentidad;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Arma datos ficticios aislados para cada prueba (cada base tiene su propio policlínico, box y profesional). */
@Component
public class Escenario {

    private static final AtomicInteger SEQ = new AtomicInteger(100);

    /** Datos base: policlínico con 2 box, una profesión, un profesional y usuarios de cada perfil. */
    public record Base(int idPoliclinico, String policlinico, int idBox, int idBox2, int idProfesion, String profesion,
                       int idProfesional, UsuarioSesion admin, UsuarioSesion tens) {
    }

    private final JdbcTemplate jdbc;
    private final AgendaService agenda;
    private final PacienteService pacientes;
    private final Reloj reloj;

    public Escenario(JdbcTemplate jdbc, AgendaService agenda, PacienteService pacientes, Reloj reloj) {
        this.jdbc = jdbc;
        this.agenda = agenda;
        this.pacientes = pacientes;
        this.reloj = reloj;
    }

    public int n() {
        return SEQ.incrementAndGet();
    }

    /** Lunes de referencia de las pruebas: al menos dos semanas en el futuro. */
    public LocalDate dia() {
        return LocalDate.now(Reloj.ZONA).plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** El reloj queda el día anterior a las 08:00: los cupos del lunes están publicados (plazo 7 días) y en el futuro. */
    public void fijarRelojAntesDelDia() {
        reloj.fijarEn(dia().minusDays(1).atTime(8, 0));
    }

    public Base base(Integer duracion) {
        int n = n();
        int perfilAdmin = jdbc.queryForObject("select id_perfil from perfil where nombre = 'Administración'", Integer.class);
        int perfilTens = jdbc.queryForObject("select id_perfil from perfil where nombre = 'TENS/TONS'", Integer.class);
        String nombrePol = "Policlínico Prueba " + n;
        int pol = insertar("insert into policlinico (nombre, direccion, comuna) values (?,?,?)", nombrePol, "Calle Falsa " + n, "Santiago");
        int box1 = insertar("insert into box (id_policlinico, nombre) values (?,?)", pol, "Box 1");
        int box2 = insertar("insert into box (id_policlinico, nombre) values (?,?)", pol, "Box 2");
        String nombreProfesion = "Medicina " + n;
        int prof = insertar("insert into profesion (nombre, duracion_min) values (?,?)", nombreProfesion, duracion);
        String hash = "$2a$10$abcdefghijklmnopqrstuuF7pD7a1zUq3m4Yb1V2c3d4e5f6g7h8i";
        int admin = insertar("insert into usuario (id_perfil, id_policlinico, nombre_completo, nombre_usuario, hash_contrasena) values (?,?,?,?,?)",
                perfilAdmin, null, "Admin " + n, "admin" + n, hash);
        int tens = insertar("insert into usuario (id_perfil, id_policlinico, nombre_completo, nombre_usuario, hash_contrasena) values (?,?,?,?,?)",
                perfilTens, pol, "Tens " + n, "tens" + n, hash);
        int profesional = profesional(prof, pol, "Paula", "Rojas " + n);
        return new Base(pol, nombrePol, box1, box2, prof, nombreProfesion, profesional,
                new UsuarioSesion(admin, "admin" + n, "Admin " + n, PerfilUsuario.ADMINISTRACION, null, null),
                new UsuarioSesion(tens, "tens" + n, "Tens " + n, PerfilUsuario.TENS, pol, nombrePol));
    }

    public Base base() {
        return base(20);
    }

    public int profesional(int idProfesion, int idPoliclinico, String nombres, String apellidos) {
        int id = insertar("insert into profesional (id_profesion, nombres, apellidos) values (?,?,?)", idProfesion, nombres, apellidos);
        jdbc.update("insert into profesional_policlinico (id_profesional, id_policlinico) values (?,?)", id, idPoliclinico);
        return id;
    }

    public static Authentication autenticacion(UsuarioSesion u) {
        return new UsernamePasswordAuthenticationToken(u, null, List.of(new SimpleGrantedAuthority(u.perfil().rol())));
    }

    public void como(UsuarioSesion u) {
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(autenticacion(u));
        SecurityContextHolder.setContext(ctx);
    }

    public void sinSesion() {
        SecurityContextHolder.clearContext();
    }

    /** Crea un bloque (como Administración) el día de la semana del lunes de prueba y devuelve los cupos de ese lunes. */
    public List<Long> bloque(Base b, String inicio, String termino) {
        return bloque(b, b.idProfesional(), b.idBox(), 1, inicio, termino);
    }

    public List<Long> bloque(Base b, int idProfesional, int idBox, int dia, String inicio, String termino) {
        var previo = SecurityContextHolder.getContext().getAuthentication();
        como(b.admin());
        agenda.crearBloque(new DatosBloque(idProfesional, idBox, dia, LocalTime.parse(inicio), LocalTime.parse(termino),
                dia().minusDays(7), dia().plusWeeks(4)));
        restaurar(previo);
        return cuposDe(idProfesional, dia().plusDays(dia - 1));
    }

    public List<Long> cuposDe(int idProfesional, LocalDate fecha) {
        return jdbc.queryForList("select c.id_cupo from cupo c join bloque_horario b on b.id_bloque = c.id_bloque "
                + "where b.id_profesional = ? and c.fecha = ? order by c.hora_inicio", Long.class, idProfesional, fecha);
    }

    public String rut() {
        String cuerpo = String.valueOf(30_000_000 + n() * 7);
        return cuerpo + "-" + DocumentoIdentidad.calcularDv(cuerpo);
    }

    public String telefono() {
        return "+5698" + String.format("%07d", n());
    }

    public DatosPaciente datos(String rut, LocalDate nacimiento, String telefono) {
        return new DatosPaciente(TipoDocumento.RUT, rut, "Rosa María", "Díaz Prueba", nacimiento, telefono, null,
                "Calle Uno 123", "Femenino", "Chilena", null);
    }

    /** Registra un paciente adulto (como TENS) y, si corresponde, su verificación HABILITADO. */
    public int paciente(Base b, boolean habilitado) {
        return paciente(b, habilitado, telefono());
    }

    public int paciente(Base b, boolean habilitado, String telefono) {
        var previo = SecurityContextHolder.getContext().getAuthentication();
        como(b.tens());
        int id = pacientes.registrar(datos(rut(), LocalDate.of(1970, 5, 10), telefono), CanalRegistro.TENS).getId();
        if (habilitado) {
            pacientes.registrarVerificacion(id, reloj.hoy(), "CESFAM Norte", true, true, null, null);
        }
        restaurar(previo);
        return id;
    }

    public void habilitar(int idPaciente, UsuarioSesion tens) {
        var previo = SecurityContextHolder.getContext().getAuthentication();
        como(tens);
        pacientes.registrarVerificacion(idPaciente, reloj.hoy(), "CESFAM Norte", true, true, null, null);
        restaurar(previo);
    }

    private static void restaurar(Authentication previo) {
        if (previo == null) {
            SecurityContextHolder.clearContext();
        } else {
            var ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(previo);
            SecurityContextHolder.setContext(ctx);
        }
    }

    public int insertar(String sql, Object... args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, holder);
        return holder.getKey().intValue();
    }
}
