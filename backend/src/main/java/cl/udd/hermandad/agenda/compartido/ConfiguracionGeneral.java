package cl.udd.hermandad.agenda.compartido;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class ConfiguracionGeneral {

    @Bean
    public Reloj reloj() {
        return Reloj.delSistema();
    }

    /** Horas en JSON como «09:30» (sección 10). */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer formatoHoras() {
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
        return b -> b.serializerByType(LocalTime.class, new LocalTimeSerializer(hhmm))
                .deserializerByType(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ISO_LOCAL_TIME));
    }
}
