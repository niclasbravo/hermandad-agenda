package cl.udd.hermandad.agenda.compartido;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SeguridadWebConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http, ObjectMapper json,
                                       SecurityContextRepository contextos) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.sameSite("Lax"));
        http
            .securityContext(sc -> sc.securityContextRepository(contextos))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                .ignoringRequestMatchers("/webhooks/**"))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/ingresar", "/api/auth/salir", "/api/auth/csrf").permitAll()
                .requestMatchers("/api/publico/**").permitAll()
                .requestMatchers("/webhooks/**").permitAll()
                .requestMatchers("/api/dev/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> escribir(res, json, HttpServletResponse.SC_UNAUTHORIZED,
                        new RespuestaError("SIN_SESION", "Tu sesión terminó. Vuelve a ingresar.", Map.of())))
                .accessDeniedHandler((req, res, ex) -> {
                    if (ex instanceof CsrfException) {
                        escribir(res, json, HttpServletResponse.SC_FORBIDDEN, new RespuestaError("SESION_VENCIDA",
                                "La página estuvo abierta mucho tiempo. Recárgala e inténtalo de nuevo.", Map.of()));
                    } else {
                        escribir(res, json, HttpServletResponse.SC_FORBIDDEN, new RespuestaError("SIN_PERMISO",
                                "Tu perfil no tiene permiso para hacer esto.", Map.of()));
                    }
                }));
        return http.build();
    }

    private static void escribir(HttpServletResponse res, ObjectMapper json, int estado, RespuestaError cuerpo)
            throws IOException {
        res.setStatus(estado);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), cuerpo);
    }
}
