package cl.udd.hermandad.agenda.demo;

import cl.udd.hermandad.agenda.agenda.dominio.ReglaReservaWeb;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.pacientes.dominio.DocumentoIdentidad;
import cl.udd.hermandad.agenda.pacientes.dominio.IAlmacenArchivos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Datos de demostración (perfil demo). Se ejecuta solo si la base está vacía. Fechas relativas a hoy.
 * Solo datos ficticios: los nombres y RUT de los pacientes vienen del prototipo.
 * Con el perfil «carga» genera 12 meses de historia y más pacientes (RNF-07: 30.000 citas al año).
 */
@Component
@Profile({"demo", "carga"})
@Order(10)
public class DatosDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemoSeeder.class);
    public static final String CONTRASENA_DEMO = "Hermandad2026!";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final IAlmacenArchivos almacen;
    private final TransactionTemplate tx;
    private final Reloj reloj;
    private final Random azar = new Random(2026);
    private final int mesesHistoria;
    private final int cantidadPacientes;
    private final boolean carga;

    // Estado interno de la carga
    private LocalDate hoy;
    private LocalDate diaDemo;
    private LocalDateTime ahora;
    private final Map<String, Integer> policlinicos = new LinkedHashMap<>();
    private final Map<String, Integer> boxes = new HashMap<>();
    private final Map<String, Integer> usuarios = new HashMap<>();
    private final Map<String, Integer> profesiones = new HashMap<>();
    private final Map<String, Integer> profesionales = new HashMap<>();
    private final Map<String, Integer> plantillas = new HashMap<>();
    private final Map<String, Long> cupos = new HashMap<>();
    private final Map<Integer, Integer> policlinicoDeBloque = new HashMap<>();
    private final Map<Integer, Integer> plazoDeBloque = new HashMap<>();
    private final Set<Long> cuposUsados = new HashSet<>();
    private final Set<String> rutsUsados = new HashSet<>();

    public DatosDemoSeeder(JdbcTemplate jdbc, PasswordEncoder encoder, IAlmacenArchivos almacen,
                           PlatformTransactionManager tm, Reloj reloj,
                           @org.springframework.beans.factory.annotation.Value("${demo.meses-historia:6}") int mesesHistoria,
                           @org.springframework.beans.factory.annotation.Value("${demo.pacientes:900}") int cantidadPacientes,
                           org.springframework.core.env.Environment entorno) {
        this.mesesHistoria = mesesHistoria;
        this.cantidadPacientes = cantidadPacientes;
        this.carga = entorno.matchesProfiles("carga");
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.almacen = almacen;
        this.tx = new TransactionTemplate(tm);
        this.reloj = reloj;
    }

    @Override
    public void run(String... args) {
        Integer n = jdbc.queryForObject("select count(*) from policlinico", Integer.class);
        if (n != null && n > 0) {
            log.info("Datos demo: la base ya tiene datos; no se cargan.");
            return;
        }
        long t0 = System.currentTimeMillis();
        tx.executeWithoutResult(s -> cargar());
        log.info("Datos demo cargados en {} s", (System.currentTimeMillis() - t0) / 1000);
    }

    void cargar() {
        hoy = reloj.hoy();
        ahora = reloj.ahora();
        diaDemo = hoy;
        while (diaDemo.getDayOfWeek() == DayOfWeek.SATURDAY || diaDemo.getDayOfWeek() == DayOfWeek.SUNDAY) {
            diaDemo = diaDemo.plusDays(1);
        }
        cargarPlantillas();
        cargarParametros();
        cargarCatalogo();
        cargarUsuarios();
        cargarProfesionalesYBloques();
        cargarCategorias();
        cargarPacientesDelPrototipo();
        cargarHistoria();
    }

    // ------------------------------------------------------------------ catálogo

    private void cargarPlantillas() {
        jdbc.query("select id_plantilla, nombre_proveedor from plantilla_mensaje",
                rs -> { plantillas.put(rs.getString(2), rs.getInt(1)); });
    }

    private void cargarParametros() {
        String sql = "update parametro_sistema set valor = ? where clave = ?";
        jdbc.update(sql, "50", "PORCENTAJE_CUPOS_WEB");
        jdbc.update(sql, null, "TOPE_RESERVAS_WEB");
        jdbc.update(sql, "1", "REFUERZOS_CANTIDAD");
        jdbc.update(sql, "4", "REFUERZOS_HORAS_ANTES");
        jdbc.update(sql, "3", "UMBRAL_INASISTENCIA");
        jdbc.update(sql, "10:00", "RECORDATORIO_HORA");
        jdbc.update(sql, "3", "MENSAJE_REINTENTOS");
    }

    private void cargarCatalogo() {
        String[][] datos = {
                {"Casa Central", "San Isidro 218", "Santiago Centro", "5"},
                {"Santa Teresa", "Gregorio Olmos 4210, esquina Manuel Chacón", "Estación Central", "3"},
                {"Sagrada Familia", "Almirante Latorre 9921, Población La Bandera", "San Ramón", "3"},
                {"Padre Hurtado", "Pasaje 7, esquina Pasaje 10, El Cortijo", "Conchalí", "3"},
                {"María Ayuda", "Profesor Alcaino 1393", "Puente Alto", "3"},
                {"Oratorio Mariano", "Av. Libertador Bernardo O'Higgins 909", "Maipú", "3"}};
        for (String[] d : datos) {
            int id = insertar("insert into policlinico (nombre, direccion, comuna) values (?,?,?)", d[0], d[1], d[2]);
            policlinicos.put(d[0], id);
            for (int b = 1; b <= Integer.parseInt(d[3]); b++) {
                boxes.put(d[0] + "|Box " + b, insertar("insert into box (id_policlinico, nombre) values (?,?)", id, "Box " + b));
            }
        }
        Object[][] prof = {{"Medicina", 20}, {"Kinesiología", 30}, {"Odontología", 30}, {"Obstetricia", 30},
                {"Psicología", 45}, {"Fonoaudiología", null}, {"Nutrición", null}, {"Podología", null}};
        for (Object[] p : prof) {
            profesiones.put((String) p[0], insertar("insert into profesion (nombre, duracion_min) values (?,?)", p[0], p[1]));
        }
    }

    private void cargarUsuarios() {
        int admin = jdbc.queryForObject("select id_perfil from perfil where nombre = 'Administración'", Integer.class);
        int tens = jdbc.queryForObject("select id_perfil from perfil where nombre = 'TENS/TONS'", Integer.class);
        String hash = encoder.encode(CONTRASENA_DEMO);
        String sql = "insert into usuario (id_perfil, id_policlinico, nombre_completo, nombre_usuario, hash_contrasena) values (?,?,?,?,?)";
        usuarios.put("l.martinez", insertar(sql, admin, null, "Laura Martínez", "l.martinez", hash));
        usuarios.put("c.munoz", insertar(sql, tens, policlinicos.get("Casa Central"), "Carolina Muñoz", "c.munoz", hash));
        String[][] otros = {{"Santa Teresa", "tens.santateresa", "Daniela Rojas"}, {"Sagrada Familia", "tens.sagradafamilia", "Marcela Soto"},
                {"Padre Hurtado", "tens.padrehurtado", "Javiera Pino"}, {"María Ayuda", "tens.mariaayuda", "Constanza Vidal"},
                {"Oratorio Mariano", "tens.oratoriomariano", "Fernanda Leiva"}};
        for (String[] o : otros) {
            usuarios.put(o[1], insertar(sql, tens, policlinicos.get(o[0]), o[2], o[1], hash));
        }
    }

    private void cargarProfesionalesYBloques() {
        LocalDate desde = hoy.minusMonths(mesesHistoria).withDayOfMonth(1);
        LocalDate hasta = hoy.getMonthValue() <= 6 ? LocalDate.of(hoy.getYear(), 6, 30) : LocalDate.of(hoy.getYear(), 12, 31);
        if (hasta.isBefore(hoy.plusMonths(1))) {
            hasta = hoy.plusMonths(2);
        }
        int[] lv = {1, 2, 3, 4, 5};
        // Casa Central (prototipo)
        prof("Paula", "Rojas", "Medicina", false, "Médica cirujana", "Universidad de Chile", "123456", "Casa Central");
        bloques("Paula Rojas", "Casa Central|Box 1", lv, "09:00", "12:00", desde, hasta);
        prof("Andrés", "Vidal", "Medicina", false, "Médico cirujano", "Pontificia Universidad Católica de Chile", "234567", "Casa Central");
        bloques("Andrés Vidal", "Casa Central|Box 5", lv, "09:00", "12:20", desde, hasta);
        bloques("Andrés Vidal", "Casa Central|Box 5", new int[]{5}, "15:00", "17:00", desde, hasta);
        prof("Tomás", "Pinto", "Kinesiología", false, "Kinesiólogo", "Universidad de Santiago de Chile", "345678", "Casa Central");
        bloques("Tomás Pinto", "Casa Central|Box 2", lv, "09:00", "12:00", desde, hasta);
        prof("Camila", "Soto", "Odontología", false, "Cirujana dentista", "Universidad de Chile", "456789", "Casa Central");
        bloques("Camila Soto", "Casa Central|Box 3", lv, "09:00", "12:00", desde, hasta);
        prof("Diego", "Vera", "Psicología", false, "Psicólogo", "Universidad Diego Portales", "567890", "Casa Central");
        bloques("Diego Vera", "Casa Central|Box 4", lv, "09:00", "12:00", desde, hasta);
        prof("Ana", "Fuentes", "Obstetricia", false, "Matrona", "Universidad de Valparaíso", "678901", "Casa Central", "Santa Teresa");
        bloques("Ana Fuentes", "Casa Central|Box 1", new int[]{2, 4}, "14:00", "17:00", desde, hasta);
        bloques("Ana Fuentes", "Santa Teresa|Box 1", new int[]{1, 3}, "09:00", "12:00", desde, hasta);
        prof("Valeria", "Paredes", "Fonoaudiología", false, "Fonoaudióloga", "Universidad de Chile", null, "Casa Central");
        // Otros policlínicos
        prof("Ignacio", "Salinas", "Medicina", false, "Médico cirujano", "Universidad de Concepción", "789012", "Santa Teresa");
        bloques("Ignacio Salinas", "Santa Teresa|Box 2", lv, "09:00", "12:00", desde, hasta);
        prof("Francisca", "Lagos", "Medicina", false, "Médica cirujana", "Universidad de Chile", "890123", "Sagrada Familia");
        bloques("Francisca Lagos", "Sagrada Familia|Box 1", new int[]{1, 3, 5}, "09:00", "12:00", desde, hasta);
        prof("Pablo", "Ortiz", "Kinesiología", false, "Kinesiólogo", "Universidad Andrés Bello", "901234", "Sagrada Familia");
        bloques("Pablo Ortiz", "Sagrada Familia|Box 2", new int[]{2, 4}, "09:00", "12:00", desde, hasta);
        prof("Cristián", "Molina", "Medicina", false, "Médico cirujano", "Universidad de Santiago de Chile", "112233", "Padre Hurtado");
        bloques("Cristián Molina", "Padre Hurtado|Box 1", new int[]{1, 2, 3, 4}, "09:00", "12:00", desde, hasta);
        prof("Daniela", "Fuentes", "Odontología", false, "Cirujana dentista", "Universidad Mayor", "223344", "Padre Hurtado");
        bloques("Daniela Fuentes", "Padre Hurtado|Box 2", new int[]{2, 4}, "14:00", "17:00", desde, hasta);
        prof("Sofía", "Herrera", "Psicología", false, "Psicóloga", "Universidad Alberto Hurtado", "334455", "María Ayuda");
        bloques("Sofía Herrera", "María Ayuda|Box 1", new int[]{1, 3}, "09:00", "12:00", desde, hasta);
        prof("Rodrigo", "Pérez", "Medicina", false, "Médico cirujano", "Universidad de Chile", "445566", "María Ayuda");
        bloques("Rodrigo Pérez", "María Ayuda|Box 2", new int[]{2, 4, 5}, "09:00", "12:00", desde, hasta);
        prof("Patricia", "Núñez", "Medicina", false, "Médica cirujana", "Universidad de los Andes", "556677", "Oratorio Mariano");
        bloques("Patricia Núñez", "Oratorio Mariano|Box 1", lv, "09:00", "12:00", desde, hasta);
        prof("Felipe", "Araya", "Kinesiología", true, null, "Universidad Católica Silva Henríquez", null, "Oratorio Mariano");
        bloques("Felipe Araya", "Oratorio Mariano|Box 2", new int[]{1, 3}, "14:00", "17:00", desde, hasta);
        if (carga) {
            String[][] tarde = {{"Paula Rojas", "Casa Central|Box 2"}, {"Ignacio Salinas", "Santa Teresa|Box 3"},
                    {"Francisca Lagos", "Sagrada Familia|Box 3"}, {"Cristián Molina", "Padre Hurtado|Box 3"},
                    {"Rodrigo Pérez", "María Ayuda|Box 3"}, {"Patricia Núñez", "Oratorio Mariano|Box 3"}};
            for (String[] t : tarde) {
                bloques(t[0], t[1], new int[]{1, 3}, "14:00", "16:00", desde, hasta);
            }
        }
        cargarIdsCupos();
        // Reunión clínica bloqueada hoy a las 11:20 (Paula Rojas)
        Long reunion = cupo("Paula Rojas", diaDemo, "11:20");
        if (reunion != null) {
            int idBloqueo = insertar("insert into bloqueo (id_usuario, motivo) values (?,?)", usuarios.get("l.martinez"), "Reunión clínica");
            jdbc.update("update cupo set id_bloqueo = ? where id_cupo = ?", idBloqueo, reunion);
            cuposUsados.add(reunion);
        }
    }

    private void prof(String nombres, String apellidos, String profesion, boolean alumno, String titulo, String casa,
                      String registro, String... pols) {
        int id = insertar("insert into profesional (id_profesion, nombres, apellidos, es_alumno_practica, titulo_profesional, "
                        + "casa_estudios, registro_prestador, plazo_publicacion_dias) values (?,?,?,?,?,?,?,7)",
                profesiones.get(profesion), nombres, apellidos, alumno, titulo, casa, registro);
        profesionales.put(nombres + " " + apellidos, id);
        for (String p : pols) {
            jdbc.update("insert into profesional_policlinico (id_profesional, id_policlinico) values (?,?)", id, policlinicos.get(p));
        }
    }

    private void bloques(String profesional, String box, int[] dias, String inicio, String termino, LocalDate desde, LocalDate hasta) {
        int idProf = profesionales.get(profesional);
        Integer duracion = jdbc.queryForObject("select pr.duracion_min from profesional p join profesion pr "
                + "on pr.id_profesion = p.id_profesion where p.id_profesional = ?", Integer.class, idProf);
        ReglaReservaWeb regla = new ReglaReservaWeb(50, null);
        String pol = box.substring(0, box.indexOf('|'));
        for (int dia : dias) {
            int idBloque = insertar("insert into bloque_horario (id_profesional, id_box, dia_semana, hora_inicio, hora_termino, "
                            + "vigencia_desde, vigencia_hasta, id_usuario) values (?,?,?,?,?,?,?,?)", idProf, boxes.get(box), dia,
                    Time.valueOf(LocalTime.parse(inicio)), Time.valueOf(LocalTime.parse(termino)), Date.valueOf(desde),
                    Date.valueOf(hasta), usuarios.get("l.martinez"));
            policlinicoDeBloque.put(idBloque, policlinicos.get(pol));
            plazoDeBloque.put(idBloque, 7);
            List<LocalTime> horas = new ArrayList<>();
            int ini = LocalTime.parse(inicio).toSecondOfDay() / 60;
            int fin = LocalTime.parse(termino).toSecondOfDay() / 60;
            for (int m = ini; m + duracion <= fin; m += duracion) {
                horas.add(LocalTime.of(m / 60, m % 60));
            }
            Set<Integer> web = new HashSet<>(regla.indicesWeb(horas.size()));
            List<Object[]> filas = new ArrayList<>();
            LocalDate f = desde;
            while (f.getDayOfWeek().getValue() != dia) f = f.plusDays(1);
            for (; !f.isAfter(hasta); f = f.plusWeeks(1)) {
                for (int i = 0; i < horas.size(); i++) {
                    LocalTime h = horas.get(i);
                    filas.add(new Object[]{idBloque, Date.valueOf(f), Time.valueOf(h), Time.valueOf(h.plusMinutes(duracion)), web.contains(i)});
                }
            }
            jdbc.batchUpdate("insert into cupo (id_bloque, fecha, hora_inicio, hora_termino, habilitado_web) values (?,?,?,?,?)", filas);
            jdbc.update("insert into auditoria (id_usuario, accion, entidad, id_registro, detalle) values (?,?,?,?,?)",
                    usuarios.get("l.martinez"), "CREAR", "bloque_horario", idBloque, "Datos de demostración");
        }
    }

    private void cargarIdsCupos() {
        jdbc.query("select c.id_cupo, b.id_profesional, c.fecha, c.hora_inicio from cupo c join bloque_horario b on b.id_bloque = c.id_bloque",
                rs -> { cupos.put(rs.getInt(2) + "|" + rs.getDate(3).toLocalDate() + "|" + rs.getTime(4).toLocalTime(), rs.getLong(1)); });
    }

    private Long cupo(String profesional, LocalDate fecha, String hora) {
        return cupos.get(profesionales.get(profesional) + "|" + fecha + "|" + LocalTime.parse(hora));
    }

    private void cargarCategorias() {
        jdbc.update("insert into categoria_etaria (nombre, edad_desde, edad_hasta) values ('0 a 17 años', 0, 17)");
        jdbc.update("insert into categoria_etaria (nombre, edad_desde, edad_hasta) values ('18 a 59 años', 18, 59)");
        jdbc.update("insert into categoria_etaria (nombre, edad_desde, edad_hasta) values ('60 años o más', 60, null)");
    }

    // ------------------------------------------------------------------ pacientes del prototipo

    private void cargarPacientesDelPrototipo() {
        int tens = usuarios.get("c.munoz");
        LocalDate verif = LocalDate.of(hoy.getYear(), 9, 2);
        if (!verif.isBefore(hoy)) verif = hoy.minusDays(60);

        int maria = paciente("12345678-5", "María", "González Pérez", hoy.minusYears(74).minusDays(40), "+56912345678",
                "Pasaje Los Aromos 123, Santiago", "Femenino", "Chilena", adulto(rutNuevo(), "Pedro González", "+56922223333"), "hijo");
        verificar(maria, verif, "CESFAM Norte");
        int juan = paciente("9876543-3", "Juan", "Soto Rivas", hoy.minusYears(66).minusDays(10), "+56987654321",
                "Av. Matta 456, Santiago", "Masculino", "Chilena", null, null);
        verificar(juan, verif.minusDays(20), "CESFAM Centro");
        int rosa = paciente("7654321-6", "Rosa", "Díaz Muñoz", hoy.minusYears(79).minusDays(100), "+56971234567",
                "Copiapó 789, Santiago", "Femenino", "Chilena", null, null);
        verificar(rosa, verif.minusDays(35), "CESFAM Norte");
        int pedro = paciente("15432109-8", "Pedro", "Lagos Vidal", hoy.minusYears(41).minusDays(5), "+56961112233",
                "Lord Cochrane 321, Santiago", "Masculino", "Chilena", null, null);
        verificar(pedro, verif.minusDays(10), "CESFAM Centro");
        int elena = paciente("18765432-7", "Elena", "Torres Pino", hoy.minusYears(33).minusDays(80), "+56962223344",
                "San Diego 654, Santiago", "Femenino", "Chilena", null, null);
        verificar(elena, verif.minusDays(15), "CESFAM Centro");
        int martin = paciente("23456789-6", "Martín", "Herrera Lagos", hoy.minusYears(12).minusDays(30), "+56963334455",
                "Nataniel Cox 987, Santiago", "Masculino", "Chilena", adulto(rutNuevo(), "Carla Herrera", "+56964445566"), "madre");
        verificar(martin, verif.minusDays(5), "CESFAM Norte");
        int luis = paciente("10203040-0", "Luis", "Campos Reyes", hoy.minusYears(58).minusDays(3), "+56965556677",
                "Sazié 147, Santiago", "Masculino", "Chilena", null, null);
        verificar(luis, verif.minusDays(40), "CESFAM Centro");
        int ana = paciente("21098765-7", "Ana", "Reyes Fuentes", hoy.minusYears(27).minusDays(60), "+56955550123",
                "Avenida España 258, Santiago", "Femenino", "Venezolana", null, null);
        verificar(ana, verif.minusDays(25), "CESFAM Centro");
        paciente("24567890-8", "Tomás", "Fuentes Vega", LocalDate.of(2017, 3, 14), "+56966667788",
                "Gay 369, Santiago", "Masculino", "Chilena", adulto("16987654-1", "Valentina Vega Rojas", "+56966667788"), "madre");
        int rosaElena = pacienteEnLinea(rutNuevo(), "Rosa Elena", "Muñoz Díaz", hoy.minusYears(52).minusDays(12), "+56967778899",
                "Maipú 741, Santiago", "Femenino", "Chilena");
        int jorge = paciente("11223344-K", "Jorge", "Ibarra Soto", hoy.minusYears(47), "+56968889900", "Bulnes 852, Santiago",
                "Masculino", "Chilena", null, null);
        verificar(jorge, verif.minusDays(50), "CESFAM Norte");
        int hector = paciente("8765432-K", "Héctor", "Molina Paz", hoy.minusYears(70), "+56969990011", "Esperanza 963, Santiago",
                "Masculino", "Chilena", null, null);
        verificar(hector, verif.minusDays(50), "CESFAM Centro");
        int camila = paciente("19283746-4", "Camila", "Rivas Ortiz", hoy.minusYears(29), "+56960001122", "Libertad 159, Santiago",
                "Femenino", "Peruana", null, null);
        verificar(camila, verif.minusDays(50), "CESFAM Norte");

        // Agenda del día de Casa Central (M-06)
        LocalDateTime reserva = diaDemo.minusDays(6).atTime(11, 5);
        long cMaria = citaHoy(maria, "Paula Rojas", "09:00", "CONFIRMADA", reserva, "ASISTIRA");
        citaHoy(juan, "Paula Rojas", "09:20", "NO_ENVIADA", reserva, null);
        citaHoy(rosa, "Tomás Pinto", "09:30", "SIN_RESPUESTA", reserva, null);
        long cPedro = citaHoy(pedro, "Paula Rojas", "09:40", "CONFIRMADA", reserva, "ASISTIRA");
        citaHoy(elena, "Tomás Pinto", "10:00", "CONFIRMADA", reserva, "ASISTIRA");
        citaHoy(martin, "Camila Soto", "10:00", "CONFIRMADA", reserva, "ASISTIRA");
        citaHoy(luis, "Paula Rojas", "10:20", "SIN_RESPUESTA", reserva, null);
        citaHoy(ana, "Diego Vera", "10:30", "NO_ENVIADA", reserva, null);
        if (!diaDemo.isAfter(hoy)) {
            jdbc.update("update cita set estado = 'ASISTIDA', fecha_asistencia = ?, id_usuario_asistencia = ? where id_cita = ?",
                    Timestamp.valueOf(ahora.isBefore(diaDemo.atTime(9, 40)) ? diaDemo.atTime(9, 40) : ahora), tens, cPedro);
        }
        // Reserva en línea pendiente de verificación, con certificados
        Long cupoRosaElena = primerCupoWebLibre("Andrés Vidal", diaDemo);
        if (cupoRosaElena != null) {
            long c = cita(rosaElena, cupoRosaElena, "EN_LINEA", null, "RESERVADA", "ENVIADA", diaDemo.minusDays(2).atTime(20, 14));
            mensaje(c, "hermandad_confirmacion_pendiente", "PACIENTE", "+56967778899", diaDemo.minusDays(2).atTime(20, 14), "ENTREGADO");
            certificado(rosaElena, "CERTIFICADO_FONASA", "certificado_fonasa.pdf", pdfEjemplo(), "application/pdf");
            certificado(rosaElena, "CERTIFICADO_CESFAM", "foto_certificado_cesfam.jpg", jpgEjemplo(), "image/jpeg");
        }
        // Historial de María: asistió, asistió, no asistió, anulada y una próxima
        LocalDate f = diaDemo.minusWeeks(16);
        citaPasada(maria, "Paula Rojas", f, "09:00", "ASISTIDA");
        citaPasada(maria, "Paula Rojas", f.plusWeeks(5), "09:20", "ASISTIDA");
        citaPasada(maria, "Paula Rojas", f.plusWeeks(9), "09:40", "INASISTIDA");
        Long cAnulada = cupoLibre("Paula Rojas", f.plusWeeks(12), "10:00");
        if (cAnulada != null) {
            long c = cita(maria, cAnulada, "TENS", tens, "RESERVADA", "CONFIRMADA", f.plusWeeks(11).atTime(10, 0));
            jdbc.update("update cita set estado = 'ANULADA', origen_anulacion = 'PACIENTE_MENSAJE', motivo_anulacion = ?, "
                    + "fecha_anulacion = ? where id_cita = ?", "Respondió «No podré asistir» por WhatsApp",
                    Timestamp.valueOf(f.plusWeeks(12).minusDays(1).atTime(10, 12)), c);
            cuposUsados.remove(cAnulada);
        }
        Long proxima = cupoLibre("Paula Rojas", diaDemo.plusWeeks(1), "09:00");
        if (proxima != null) {
            long c = cita(maria, proxima, "TENS", tens, "RESERVADA", "ENVIADA", ahora.minusDays(1));
            mensaje(c, "hermandad_confirmacion", "PACIENTE", "+56912345678", ahora.minusDays(1), "ENTREGADO");
            mensaje(c, "hermandad_confirmacion", "ADULTO_RESPONSABLE", "+56922223333", ahora.minusDays(1), "ENTREGADO");
            programado(c, "hermandad_recordatorio", "PACIENTE", "+56912345678", diaDemo.plusWeeks(1).minusDays(1).atTime(10, 0));
            programado(c, "hermandad_recordatorio", "ADULTO_RESPONSABLE", "+56922223333", diaDemo.plusWeeks(1).minusDays(1).atTime(10, 0));
        }
        // Listado de inasistencia reiterada (umbral 3 en el mes)
        List<LocalDate> delMes = diasHabilesPasadosDelMes(4);
        inasistencias(luis, delMes, 3);
        inasistencias(jorge, delMes, 4);
        inasistencias(hector, delMes, 3);
        inasistencias(camila, delMes, 3);
        // Lista de espera de Casa Central
        String[][] espera = {{"Gloria", "Fuentes Lagos", "Medicina"}, {"Raúl", "Ortega Vidal", "Kinesiología"},
                {"Marta", "Cifuentes Rey", "Psicología"}, {"Teresa", "Aguilar Rojas", "Medicina"},
                {"Benjamín", "Silva Núñez", "Odontología"}, {"Ana", "Morales Pinto", "Medicina"}};
        int i = 0;
        for (String[] e : espera) {
            int p = paciente(rutNuevo(), e[0], e[1], hoy.minusYears(30 + azar.nextInt(45)).minusDays(azar.nextInt(300)),
                    telefono(), direccion(), e[0].endsWith("a") ? "Femenino" : "Masculino", "Chilena", null, null);
            verificar(p, verif.minusDays(i), "CESFAM Norte");
            LocalDateTime cuando = ahora.minusDays(12 - i * 2L).withHour(9 + i).withMinute(15);
            int idIns = insertar("insert into inscripcion_espera (id_paciente, id_policlinico, id_profesion, fecha_inscripcion, "
                    + "fecha_orden, id_usuario) values (?,?,?,?,?,?)", p, policlinicos.get("Casa Central"), profesiones.get(e[2]),
                    Timestamp.valueOf(cuando), Timestamp.valueOf(cuando), tens);
            jdbc.update("insert into movimiento_espera (id_inscripcion, tipo, id_usuario, fecha_hora) values (?,?,?,?)",
                    idIns, "INSCRIPCION", tens, Timestamp.valueOf(cuando));
            i++;
        }
    }

    private long citaHoy(int paciente, String profesional, String hora, String confirmacion, LocalDateTime reserva, String respuesta) {
        Long idCupo = cupo(profesional, diaDemo, hora);
        if (idCupo == null || cuposUsados.contains(idCupo)) {
            return -1;
        }
        long c = cita(paciente, idCupo, "TENS", usuarios.get("c.munoz"), "RESERVADA", confirmacion, reserva);
        String[] tel = jdbc.queryForObject("select p.telefono_celular, a.telefono from paciente p left join adulto_responsable a "
                + "on a.id_adulto = p.id_adulto_responsable where p.id_paciente = ?", (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, paciente);
        LocalDateTime recordatorio = diaDemo.minusDays(1).atTime(10, 0);
        if ("NO_ENVIADA".equals(confirmacion)) {
            mensaje(c, "hermandad_confirmacion", "PACIENTE", tel[0], reserva.plusMinutes(3), "FALLIDO");
            return c;
        }
        mensaje(c, "hermandad_confirmacion", "PACIENTE", tel[0], reserva, "LEIDO");
        if (tel[1] != null) mensaje(c, "hermandad_confirmacion", "ADULTO_RESPONSABLE", tel[1], reserva, "ENTREGADO");
        if (recordatorio.isBefore(ahora)) {
            long r = mensaje(c, "hermandad_recordatorio", "PACIENTE", tel[0], recordatorio, "ENTREGADO");
            if (tel[1] != null) mensaje(c, "hermandad_recordatorio", "ADULTO_RESPONSABLE", tel[1], recordatorio, "ENTREGADO");
            if (respuesta != null) {
                jdbc.update("insert into respuesta_mensaje (id_mensaje, respuesta, fecha_hora) values (?,?,?)", r, respuesta,
                        Timestamp.valueOf(recordatorio.plusMinutes(7 + azar.nextInt(40))));
            }
        }
        return c;
    }

    private void citaPasada(int paciente, String profesional, LocalDate fecha, String hora, String estado) {
        Long idCupo = cupoLibre(profesional, fecha, hora);
        if (idCupo == null) return;
        long c = cita(paciente, idCupo, "TENS", usuarios.get("c.munoz"), "RESERVADA", "CONFIRMADA", fecha.minusDays(7).atTime(10, 30));
        jdbc.update("update cita set estado = ?, fecha_asistencia = ?, id_usuario_asistencia = ? where id_cita = ?", estado,
                Timestamp.valueOf(fecha.atTime(LocalTime.parse(hora)).plusMinutes(25)), usuarios.get("c.munoz"), c);
    }

    private void inasistencias(int paciente, List<LocalDate> dias, int cantidad) {
        String[] profs = {"Paula Rojas", "Tomás Pinto", "Camila Soto", "Diego Vera"};
        int hechas = 0;
        for (int i = 0; i < dias.size() * 4 && hechas < cantidad; i++) {
            LocalDate d = dias.get(i % dias.size());
            String prof = profs[(paciente + i) % profs.length];
            Long idCupo = primerCupoLibre(prof, d);
            if (idCupo == null) continue;
            long c = cita(paciente, idCupo, "TENS", usuarios.get("c.munoz"), "RESERVADA", "SIN_RESPUESTA", d.minusDays(5).atTime(9, 0));
            jdbc.update("update cita set estado = 'INASISTIDA', fecha_asistencia = ?, id_usuario_asistencia = ? where id_cita = ?",
                    Timestamp.valueOf(d.atTime(13, 0)), usuarios.get("c.munoz"), c);
            hechas++;
        }
    }

    private List<LocalDate> diasHabilesPasadosDelMes(int minimo) {
        List<LocalDate> dias = new ArrayList<>();
        LocalDate d = hoy.minusDays(1);
        LocalDate inicio = hoy.withDayOfMonth(1);
        while (dias.size() < 8 && (!d.isBefore(inicio) || dias.size() < minimo)) {
            if (d.getDayOfWeek().getValue() <= 5) dias.add(d);
            d = d.minusDays(1);
        }
        return dias;
    }

    // ------------------------------------------------------------------ historia de 6 meses

    private void cargarHistoria() {
        String[] nombresF = {"Carmen", "Patricia", "Claudia", "Paola", "Andrea", "Marcela", "Verónica", "Gloria", "Isabel", "Sonia",
                "Javiera", "Constanza", "Catalina", "Francisca", "Valentina", "Fernanda", "Daniela", "Camila", "Josefa", "Antonia",
                "Luisa", "Margarita", "Elena", "Teresa", "Silvia", "Rosa", "Ximena", "Lorena", "Carolina", "Mónica"};
        String[] nombresM = {"José", "Juan", "Luis", "Carlos", "Jorge", "Manuel", "Pedro", "Francisco", "Miguel", "Ricardo",
                "Sergio", "Patricio", "Hernán", "Raúl", "Víctor", "Matías", "Benjamín", "Vicente", "Agustín", "Joaquín",
                "Cristóbal", "Felipe", "Sebastián", "Diego", "Nicolás", "Tomás", "Alonso", "Gabriel", "Andrés", "Claudio"};
        String[] apellidos = {"González", "Muñoz", "Rojas", "Díaz", "Pérez", "Soto", "Contreras", "Silva", "Martínez", "Sepúlveda",
                "Morales", "Rodríguez", "López", "Fuentes", "Hernández", "Torres", "Araya", "Flores", "Espinoza", "Valenzuela",
                "Castillo", "Tapia", "Reyes", "Gutiérrez", "Castro", "Pizarro", "Álvarez", "Vásquez", "Sánchez", "Fernández",
                "Ramírez", "Carrasco", "Gómez", "Cortés", "Herrera", "Núñez", "Jara", "Vergara", "Rivera", "Figueroa"};
        String[] nacionalidades = {"Venezolana", "Peruana", "Haitiana", "Colombiana", "Boliviana"};
        int tensCC = usuarios.get("c.munoz");
        // Pacientes ficticios para la historia
        List<Integer> pool = new ArrayList<>();
        List<Object[]> verifs = new ArrayList<>();
        for (int i = 0; i < cantidadPacientes; i++) {
            boolean mujer = azar.nextDouble() < 0.62;
            String nombre = mujer ? nombresF[azar.nextInt(nombresF.length)] : nombresM[azar.nextInt(nombresM.length)];
            String ap = apellidos[azar.nextInt(apellidos.length)] + " " + apellidos[azar.nextInt(apellidos.length)];
            double r = azar.nextDouble();
            int edad = r < 0.13 ? 2 + azar.nextInt(16) : r < 0.62 ? 18 + azar.nextInt(42) : 60 + azar.nextInt(30);
            LocalDate nac = hoy.minusYears(edad).minusDays(azar.nextInt(360));
            Integer adulto = null;
            String relacion = null;
            if (edad < 18) {
                adulto = adulto(rutNuevo(), (azar.nextBoolean() ? "Carolina " : "Rodrigo ") + ap, telefono());
                relacion = azar.nextBoolean() ? "madre" : "padre";
            }
            String nacionalidad = azar.nextDouble() < 0.86 ? "Chilena" : nacionalidades[azar.nextInt(nacionalidades.length)];
            String genero = azar.nextDouble() < 0.01 ? "Otro" : mujer ? "Femenino" : "Masculino";
            int id = paciente(rutNuevo(), nombre, ap, nac, telefono(), direccion(), genero, nacionalidad, adulto, relacion);
            pool.add(id);
            verifs.add(new Object[]{id, tensCC, Date.valueOf(hoy.minusMonths(7).plusDays(azar.nextInt(20))),
                    azar.nextBoolean() ? "CESFAM Norte" : "CESFAM Centro"});
        }
        jdbc.batchUpdate("insert into verificacion_requisitos (id_paciente, id_usuario, fecha_verificacion, cesfam_inscripcion, "
                + "fonasa_vigente, inscrito_cesfam, resultado) values (?,?,?,?,true,true,'HABILITADO')", verifs);

        Map<Integer, Integer> tensDePol = new HashMap<>();
        jdbc.query("select id_policlinico, min(id_usuario) from usuario where id_policlinico is not null group by id_policlinico",
                rs -> { tensDePol.put(rs.getInt(1), rs.getInt(2)); });
        // Cupos pasados (y de la próxima semana) sin cita
        List<Object[]> citas = new ArrayList<>();
        List<Object[]> filasCupo = jdbc.query("select c.id_cupo, c.id_bloque, c.fecha, c.hora_inicio, c.habilitado_web from cupo c "
                        + "where c.id_bloqueo is null and c.fecha < ? order by c.fecha, c.hora_inicio",
                (rs, n) -> new Object[]{rs.getLong(1), rs.getInt(2), rs.getDate(3).toLocalDate(), rs.getTime(4).toLocalTime(), rs.getBoolean(5)},
                Date.valueOf(diaDemo.plusDays(8)));
        for (Object[] fc : filasCupo) {
            long idCupo = (Long) fc[0];
            if (cuposUsados.contains(idCupo)) continue;
            LocalDate fecha = (LocalDate) fc[2];
            LocalTime hora = (LocalTime) fc[3];
            int pol = policlinicoDeBloque.get((Integer) fc[1]);
            int tens = tensDePol.get(pol);
            int paciente = pool.get(azar.nextInt(pool.size()));
            boolean pasado = fecha.isBefore(hoy) || (fecha.equals(hoy) && false);
            if (fecha.equals(diaDemo) && pol == policlinicos.get("Casa Central")) continue;
            if (fecha.equals(hoy) || fecha.equals(diaDemo)) continue;
            boolean web = (Boolean) fc[4] && azar.nextDouble() < 0.25;
            LocalDateTime reserva = fecha.minusDays(2 + azar.nextInt(10)).atTime(9 + azar.nextInt(8), azar.nextInt(60));
            if (reserva.isAfter(ahora)) reserva = ahora.minusHours(1 + azar.nextInt(20));
            if (pasado) {
                if (azar.nextDouble() > 0.95) continue;
                double r = azar.nextDouble();
                String estado = r < 0.04 ? "ANULADA" : r < 0.04 + 0.816 ? "ASISTIDA" : "INASISTIDA";
                String conf = switch (estado) {
                    case "ASISTIDA" -> azar.nextDouble() < 0.7 ? "CONFIRMADA" : azar.nextDouble() < 0.4 ? "CONFIRMADA_TELEFONO" : "SIN_RESPUESTA";
                    case "INASISTIDA" -> azar.nextDouble() < 0.5 ? "SIN_RESPUESTA" : azar.nextDouble() < 0.3 ? "NO_ENVIADA" : "CONFIRMADA";
                    default -> "ENVIADA";
                };
                Timestamp asistencia = estado.equals("ANULADA") ? null : Timestamp.valueOf(fecha.atTime(hora).plusMinutes(30));
                Timestamp anulacion = estado.equals("ANULADA") ? Timestamp.valueOf(fecha.minusDays(1).atTime(10, 20)) : null;
                citas.add(new Object[]{paciente, idCupo, web ? "EN_LINEA" : "TENS", web ? null : tens, estado, conf,
                        Timestamp.valueOf(reserva), estado.equals("ANULADA") ? "PACIENTE_MENSAJE" : null, anulacion,
                        asistencia, asistencia == null ? null : tens});
            } else if (azar.nextDouble() < 0.45) {
                citas.add(new Object[]{paciente, idCupo, web ? "EN_LINEA" : "TENS", web ? null : tens, "RESERVADA", "ENVIADA",
                        Timestamp.valueOf(reserva), null, null, null, null});
            }
        }
        jdbc.batchUpdate("insert into cita (id_paciente, id_cupo, canal_reserva, id_usuario_reserva, estado, estado_confirmacion, "
                + "fecha_reserva, origen_anulacion, fecha_anulacion, fecha_asistencia, id_usuario_asistencia) "
                + "values (?,?,?,?,?,?,?,?,?,?,?)", citas);
        // Recordatorios programados para las citas futuras de la historia
        jdbc.update("""
                insert into mensaje (id_cita, id_plantilla, destinatario, telefono_destino, fecha_programada)
                select ci.id_cita, ?, 'PACIENTE', pa.telefono_celular, timestamp(date_sub(c.fecha, interval 1 day), '10:00:00')
                from cita ci join cupo c on c.id_cupo = ci.id_cupo join paciente pa on pa.id_paciente = ci.id_paciente
                where ci.estado = 'RESERVADA' and timestamp(date_sub(c.fecha, interval 1 day), '10:00:00') > ?
                  and not exists (select 1 from mensaje m where m.id_cita = ci.id_cita)""",
                plantillas.get("hermandad_recordatorio"), Timestamp.valueOf(ahora));
        log.info("Datos demo: {} pacientes y {} citas de historia", pool.size(), citas.size());
    }

    // ------------------------------------------------------------------ apoyo

    private int paciente(String rut, String nombres, String apellidos, LocalDate nacimiento, String telefono, String direccion,
                         String genero, String nacionalidad, Integer adulto, String relacion) {
        rutsUsados.add(rut);
        return insertar("insert into paciente (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, "
                        + "telefono_celular, direccion, genero, nacionalidad, id_adulto_responsable, relacion_adulto, "
                        + "canal_registro, id_usuario_registro, fecha_aviso_mensajes, fecha_registro) values ('RUT',?,?,?,?,?,?,?,?,?,?,'TENS',?,?,?)",
                rut, nombres, apellidos, Date.valueOf(nacimiento), telefono, direccion, genero, nacionalidad, adulto, relacion,
                usuarios.get("c.munoz"), Timestamp.valueOf(ahora.minusMonths(8)), Timestamp.valueOf(ahora.minusMonths(8)));
    }

    private int pacienteEnLinea(String rut, String nombres, String apellidos, LocalDate nacimiento, String telefono,
                                String direccion, String genero, String nacionalidad) {
        rutsUsados.add(rut);
        LocalDateTime cuando = diaDemo.minusDays(2).atTime(20, 10);
        return insertar("insert into paciente (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, "
                        + "telefono_celular, direccion, genero, nacionalidad, canal_registro, fecha_aviso_mensajes, fecha_registro) "
                        + "values ('RUT',?,?,?,?,?,?,?,?,'EN_LINEA',?,?)",
                rut, nombres, apellidos, Date.valueOf(nacimiento), telefono, direccion, genero, nacionalidad,
                Timestamp.valueOf(cuando), Timestamp.valueOf(cuando));
    }

    private Integer adulto(String rut, String nombre, String telefono) {
        rutsUsados.add(rut);
        return insertar("insert into adulto_responsable (tipo_documento, numero_documento, nombre_completo, telefono) values ('RUT',?,?,?)",
                rut, nombre, telefono);
    }

    private void verificar(int paciente, LocalDate fecha, String cesfam) {
        int id = insertar("insert into verificacion_requisitos (id_paciente, id_usuario, fecha_verificacion, cesfam_inscripcion, "
                        + "fonasa_vigente, inscrito_cesfam, resultado, fecha_registro) values (?,?,?,?,true,true,'HABILITADO',?)",
                paciente, usuarios.get("c.munoz"), Date.valueOf(fecha), cesfam, Timestamp.valueOf(fecha.atTime(10, 15)));
        jdbc.update("insert into auditoria (id_usuario, fecha_hora, accion, entidad, id_registro, detalle) values (?,?,?,?,?,?)",
                usuarios.get("c.munoz"), Timestamp.valueOf(fecha.atTime(10, 15)), "VERIFICAR", "verificacion_requisitos", id,
                "Paciente " + paciente + ": HABILITADO, " + cesfam);
    }

    private long cita(int paciente, long idCupo, String canal, Integer usuario, String estado, String confirmacion, LocalDateTime reserva) {
        cuposUsados.add(idCupo);
        long id = insertar("insert into cita (id_paciente, id_cupo, canal_reserva, id_usuario_reserva, estado, estado_confirmacion, "
                + "fecha_reserva) values (?,?,?,?,?,?,?)", paciente, idCupo, canal, usuario, estado, confirmacion, Timestamp.valueOf(reserva));
        jdbc.update("insert into auditoria (id_usuario, fecha_hora, accion, entidad, id_registro, detalle) values (?,?,?,?,?,?)",
                usuario, Timestamp.valueOf(reserva), "CREAR", "cita", id, "Paciente " + paciente + ", cupo " + idCupo + ", canal " + canal);
        return id;
    }

    private long mensaje(long cita, String plantilla, String destinatario, String telefono, LocalDateTime cuando, String estado) {
        return insertar("insert into mensaje (id_cita, id_plantilla, destinatario, telefono_destino, fecha_programada, fecha_envio, "
                        + "estado_entrega, id_externo) values (?,?,?,?,?,?,?,?)", cita, plantillas.get(plantilla), destinatario, telefono,
                Timestamp.valueOf(cuando), Timestamp.valueOf(cuando.plusSeconds(20)), estado,
                "FALLIDO".equals(estado) ? null : "sim.demo." + UUID.randomUUID());
    }

    private void programado(long cita, String plantilla, String destinatario, String telefono, LocalDateTime cuando) {
        jdbc.update("insert into mensaje (id_cita, id_plantilla, destinatario, telefono_destino, fecha_programada) values (?,?,?,?,?)",
                cita, plantillas.get(plantilla), destinatario, telefono, Timestamp.valueOf(cuando));
    }

    private void certificado(int paciente, String tipo, String nombre, byte[] contenido, String mime) {
        String ruta = almacen.guardar(contenido, mime.equals("application/pdf") ? "pdf" : "jpg");
        jdbc.update("insert into documento_adjunto (id_paciente, tipo, nombre_archivo, tipo_mime, tamano_bytes, ruta_almacenamiento, "
                + "fecha_carga) values (?,?,?,?,?,?,?)", paciente, tipo, nombre, mime, contenido.length, ruta,
                Timestamp.valueOf(diaDemo.minusDays(2).atTime(20, 16)));
    }

    private Long cupoLibre(String profesional, LocalDate fecha, String hora) {
        Long id = cupo(profesional, fecha, hora);
        return id == null || cuposUsados.contains(id) ? null : id;
    }

    private Long primerCupoLibre(String profesional, LocalDate fecha) {
        int idProf = profesionales.get(profesional);
        return cupos.entrySet().stream()
                .filter(e -> e.getKey().startsWith(idProf + "|" + fecha + "|") && !cuposUsados.contains(e.getValue()))
                .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private Long primerCupoWebLibre(String profesional, LocalDate fecha) {
        List<Long> ids = jdbc.queryForList("select c.id_cupo from cupo c join bloque_horario b on b.id_bloque = c.id_bloque "
                + "where b.id_profesional = ? and c.fecha = ? and c.habilitado_web and c.id_bloqueo is null and c.hora_inicio >= '10:40' "
                + "order by c.hora_inicio", Long.class, profesionales.get(profesional), Date.valueOf(fecha));
        return ids.stream().filter(i -> !cuposUsados.contains(i)).findFirst().orElse(null);
    }

    private String rutNuevo() {
        while (true) {
            String cuerpo = String.valueOf(5_000_000 + azar.nextInt(22_000_000));
            String rut = cuerpo + "-" + DocumentoIdentidad.calcularDv(cuerpo);
            if (rutsUsados.add(rut)) return rut;
        }
    }

    private String telefono() {
        String t;
        do {
            t = "+569" + (10_000_000 + azar.nextInt(89_999_999));
        } while (t.endsWith("0000"));
        return t;
    }

    private String direccion() {
        String[] calles = {"Los Aromos", "Las Acacias", "San Pablo", "Santa Rosa", "Gran Avenida", "Vicuña Mackenna", "Recoleta",
                "Independencia", "Pajaritos", "Departamental", "La Florida", "Américo Vespucio"};
        return calles[azar.nextInt(calles.length)] + " " + (100 + azar.nextInt(9800));
    }

    private int insertar(String sql, Object... args) {
        var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, holder);
        return holder.getKey().intValue();
    }

    static byte[] pdfEjemplo() {
        String pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 144]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
                + "4 0 obj<</Length 66>>stream\nBT /F1 14 Tf 20 80 Td (Certificado FONASA - ejemplo ficticio) Tj ET\nendstream endobj\n"
                + "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] jpgEjemplo() {
        try {
            var img = new java.awt.image.BufferedImage(320, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 320, 200);
            g.setColor(new java.awt.Color(0, 104, 122));
            g.drawString("Certificado de inscripción CESFAM", 20, 90);
            g.drawString("Ejemplo ficticio", 20, 115);
            g.dispose();
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        }
    }
}
