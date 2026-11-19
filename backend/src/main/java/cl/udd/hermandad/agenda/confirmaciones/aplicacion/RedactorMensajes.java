package cl.udd.hermandad.agenda.confirmaciones.aplicacion;

import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.compartido.Textos;
import cl.udd.hermandad.agenda.confirmaciones.dominio.PlantillaMensaje;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma el texto y los parámetros de cada plantilla. Solo nombre de pila, fecha, hora, profesión, profesional,
 * policlínico, box y dirección: sin información clínica (RNF-06) ni abreviaturas (RNF-02).
 */
public final class RedactorMensajes {

    /** Orden de los parámetros que se envían a la plantilla aprobada en WhatsApp. */
    public static final List<String> ORDEN_PARAMETROS =
            List.of("nombre", "Fecha", "hora", "profesion", "profesional", "policlinico", "box", "direccion", "fecha");

    public static final List<String> BOTONES = List.of("Asistiré", "No podré asistir");

    private RedactorMensajes() {
    }

    public static Map<String, String> variables(Cita cita) {
        Cupo c = cita.getCupo();
        Policlinico pol = c.getBloque().getBox().getPoliclinico();
        Map<String, String> v = new LinkedHashMap<>();
        v.put("nombre", cita.getPaciente().nombrePila());
        String fecha = Textos.fechaLarga(c.getFecha());
        v.put("fecha", fecha);
        v.put("Fecha", Textos.capitalizar(fecha));
        v.put("hora", Textos.hora(c.getHoraInicio()));
        v.put("profesion", c.getBloque().getProfesional().getProfesion().getNombre());
        v.put("profesional", c.getBloque().getProfesional().nombreCompleto());
        v.put("policlinico", pol.getNombre());
        v.put("box", c.getBloque().getBox().getNombre());
        v.put("direccion", pol.getDireccion() + ", " + pol.getComuna());
        return v;
    }

    public static List<String> parametros(Cita cita) {
        Map<String, String> v = variables(cita);
        return ORDEN_PARAMETROS.stream().map(v::get).toList();
    }

    public static String texto(PlantillaMensaje plantilla, Cita cita, Paciente.Destinatario destinatario) {
        String t = plantilla.getTexto();
        if (destinatario == Paciente.Destinatario.ADULTO_RESPONSABLE) {
            t = t.replace("Hola, {{nombre}}. Su hora quedó reservada.", "Hola. La hora de {{nombre}} quedó reservada.")
                 .replace("Hola, {{nombre}}. Su hora del", "Hola. La hora de {{nombre}} del")
                 .replace("Recuerde su hora", "Recuerde la hora de {{nombre}}")
                 .replace("Anulamos su hora", "Anulamos la hora de {{nombre}}")
                 .replace("Si no puede asistir", "Si no puede asistir")
                 .replace("Para su primera atención, traiga su certificado", "Para su primera atención, traiga el certificado");
        }
        for (Map.Entry<String, String> e : variables(cita).entrySet()) {
            t = t.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return t;
    }

    /** Plantillas iniciales (sección 11.4; textos del prototipo M-05). */
    public static List<String[]> plantillasIniciales() {
        String cuerpoConfirmacion = "Hola, {{nombre}}. Su hora quedó reservada.\n{{Fecha}}, {{hora}}\n"
                + "{{profesion}} con {{profesional}}\nPoliclínico {{policlinico}}, {{box}}\n{{direccion}}\n"
                + "Si no puede asistir, avísenos para dar la hora a otra persona.";
        return List.of(
                new String[]{"CONFIRMACION", PlantillaMensaje.CONFIRMACION, cuerpoConfirmacion},
                new String[]{"CONFIRMACION", PlantillaMensaje.CONFIRMACION_PENDIENTE, cuerpoConfirmacion
                        + "\nPara su primera atención, traiga su certificado de FONASA y su certificado de inscripción en el CESFAM."},
                new String[]{"RECORDATORIO", PlantillaMensaje.RECORDATORIO, "Recuerde su hora de mañana:\n{{Fecha}}, {{hora}}\n"
                        + "{{profesion}}, Policlínico {{policlinico}}, {{box}}.\n¿Podrá asistir?"},
                new String[]{"REFUERZO", PlantillaMensaje.REFUERZO, "Recuerde su hora del {{fecha}}:\n{{Fecha}}, {{hora}}\n"
                        + "{{profesion}}, Policlínico {{policlinico}}, {{box}}.\n¿Podrá asistir?"},
                new String[]{"ANULACION", PlantillaMensaje.ANULACION, "Gracias por avisar. Anulamos su hora del {{fecha}} a las {{hora}}.\n"
                        + "Si necesita una nueva hora, escriba o llame al policlínico."},
                new String[]{"ANULACION", PlantillaMensaje.ANULACION_POLICLINICO, "Hola, {{nombre}}. Su hora del {{fecha}} a las {{hora}} "
                        + "fue anulada por el policlínico. Escriba o llame al policlínico para darle una nueva hora."});
    }
}
