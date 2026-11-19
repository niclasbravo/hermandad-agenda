package cl.udd.hermandad.agenda.confirmaciones.dominio;

import java.util.List;

/** Puerto de mensajería (patrón Adapter). El dominio no conoce el proveedor de WhatsApp. */
public interface IServicioMensajeria {

    /**
     * Envía una plantilla aprobada. Devuelve el identificador externo del mensaje.
     * Lanza {@link ErrorEnvioMensaje} si el proveedor rechaza el envío.
     */
    String enviarPlantilla(String telefono, String plantilla, List<String> parametros, List<String> botones);

    class ErrorEnvioMensaje extends RuntimeException {
        public ErrorEnvioMensaje(String mensaje) {
            super(mensaje);
        }

        public ErrorEnvioMensaje(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
