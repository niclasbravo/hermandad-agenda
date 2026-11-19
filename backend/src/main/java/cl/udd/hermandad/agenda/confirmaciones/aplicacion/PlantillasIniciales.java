package cl.udd.hermandad.agenda.confirmaciones.aplicacion;

import cl.udd.hermandad.agenda.confirmaciones.dominio.IMensajeRepository;
import cl.udd.hermandad.agenda.confirmaciones.dominio.PlantillaMensaje;
import cl.udd.hermandad.agenda.confirmaciones.dominio.TipoPlantilla;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Carga las plantillas de la sección 11.4 si no existen (en cualquier perfil). */
@Component
@Order(2)
public class PlantillasIniciales implements ApplicationRunner {

    private final IMensajeRepository mensajes;

    public PlantillasIniciales(IMensajeRepository mensajes) {
        this.mensajes = mensajes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        asegurar();
    }

    @Transactional
    public void asegurar() {
        for (String[] p : RedactorMensajes.plantillasIniciales()) {
            if (mensajes.plantilla(p[1]).isEmpty()) {
                mensajes.guardarPlantilla(new PlantillaMensaje(TipoPlantilla.valueOf(p[0]), p[1], p[2]));
            }
        }
    }
}
