package cl.udd.hermandad.agenda.integracion;

import cl.udd.hermandad.agenda.compartido.LimiteIntentos;
import cl.udd.hermandad.agenda.compartido.Reloj;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

/**
 * Base de las pruebas de integración. Un solo contenedor MySQL 8.0 para todas las clases (singleton),
 * con el esquema creado por db/01 y db/02 tal cual (sin Flyway ni Hibernate: ddl-auto=validate).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaIntegracion {

    static final Path DB = Path.of("..", "db").toAbsolutePath().normalize();

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hermandad_agenda")
            .withUsername("agenda")
            .withPassword("agenda")
            .withEnv("TZ", "America/Santiago")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_spanish_ci")
            .withCopyFileToContainer(MountableFile.forHostPath(DB.resolve("01_esquema_hermandad.sql")),
                    "/docker-entrypoint-initdb.d/01_esquema_hermandad.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(DB.resolve("02_reglas_hermandad.sql")),
                    "/docker-entrypoint-initdb.d/02_reglas_hermandad.sql");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?serverTimezone=America/Santiago&characterEncoding=UTF-8&connectionCollation=utf8mb4_spanish_ci"
                + "&useSSL=false&allowPublicKeyRetrieval=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected Escenario esc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected Reloj reloj;
    @Autowired
    protected LimiteIntentos limite;

    @BeforeEach
    void prepararBase() {
        esc.fijarRelojAntesDelDia();
        limite.reiniciar();
    }

    @AfterEach
    void limpiarBase() {
        SecurityContextHolder.clearContext();
        reloj.usarSistema();
    }

    protected long contar(String sql, Object... args) {
        Number n = jdbc.queryForObject(sql, Number.class, args);
        return n == null ? 0 : n.longValue();
    }

    protected String texto(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
