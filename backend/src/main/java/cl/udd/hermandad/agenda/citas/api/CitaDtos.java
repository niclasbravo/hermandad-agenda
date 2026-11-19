package cl.udd.hermandad.agenda.citas.api;

import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.citas.dominio.*;
import cl.udd.hermandad.agenda.compartido.Textos;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import cl.udd.hermandad.agenda.pacientes.dominio.Telefono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class CitaDtos {

    private CitaDtos() {
    }

    public record CitaDto(Long id, EstadoCita estado, String estadoTexto, EstadoConfirmacion estadoConfirmacion,
                          CanalReserva canalReserva, LocalDate fecha, LocalTime horaInicio, LocalTime horaTermino,
                          int duracion, Long idCupo, Integer idPaciente, String paciente, String nombrePila, String iniciales,
                          String documento, String telefono, String adultoResponsable, String relacionAdulto,
                          String telefonoAdulto, Integer idProfesional, String profesional, Integer idProfesion,
                          String profesion, Integer idBox, String box, Integer idPoliclinico, String policlinico,
                          String direccion, Integer idSerie, LocalDateTime fechaReserva, OrigenAnulacion origenAnulacion,
                          String motivoAnulacion, LocalDateTime fechaAnulacion, LocalDateTime fechaAsistencia) {

        public static CitaDto de(Cita c, LocalDate hoy) {
            Cupo cu = c.getCupo();
            Paciente p = c.getPaciente();
            Policlinico pol = cu.getBloque().getBox().getPoliclinico();
            var adulto = p.getAdultoResponsable();
            return new CitaDto(c.getId(), c.getEstado(), estadoTexto(c, hoy), c.getEstadoConfirmacion(), c.getCanalReserva(),
                    cu.getFecha(), cu.getHoraInicio(), cu.getHoraTermino(), cu.duracionMinutos(), cu.getId(), p.getId(),
                    p.nombreCompleto(), p.nombrePila(), p.iniciales(), p.getDocumento().formatear(),
                    Telefono.formatear(p.getTelefonoCelular()),
                    adulto == null ? null : adulto.getNombreCompleto(), p.getRelacionAdulto(),
                    adulto == null ? null : Telefono.formatear(adulto.getTelefono()),
                    cu.getBloque().getProfesional().getId(), cu.getBloque().getProfesional().nombreCompleto(),
                    cu.getBloque().getProfesional().getProfesion().getId(),
                    cu.getBloque().getProfesional().getProfesion().getNombre(), cu.getBloque().getBox().getId(),
                    cu.getBloque().getBox().getNombre(), pol.getId(), pol.getNombre(), pol.getDireccion() + ", " + pol.getComuna(),
                    c.getSerie() == null ? null : c.getSerie().getId(), c.getFechaReserva(), c.getOrigenAnulacion(),
                    c.getMotivoAnulacion(), c.getFechaAnulacion(), c.getFechaAsistencia());
        }

        /** RF-23: Próxima, Asistió, No asistió, Anulada y Pendiente de verificación. */
        static String estadoTexto(Cita c, LocalDate hoy) {
            return switch (c.getEstado()) {
                case PENDIENTE_VERIFICACION -> "Pendiente de verificación";
                case ANULADA -> "Anulada";
                case ASISTIDA -> "Asistió";
                case INASISTIDA -> "No asistió";
                case RESERVADA -> c.getCupo().getFecha().isBefore(hoy) ? "Sin registro de asistencia" : "Próxima";
            };
        }
    }

    public record ComprobanteDto(Long idCita, String estado, String nombrePila, String policlinico, String direccion,
                                 String profesional, String profesion, String box, LocalDate fecha, LocalTime hora,
                                 String fechaTexto, int duracion, boolean pendienteVerificacion, String mensaje) {
        public static ComprobanteDto de(Cita c) {
            Cupo cu = c.getCupo();
            Policlinico pol = cu.getBloque().getBox().getPoliclinico();
            boolean pendiente = c.getEstado() == EstadoCita.PENDIENTE_VERIFICACION;
            return new ComprobanteDto(c.getId(), c.getEstado().name(), c.getPaciente().nombrePila(), pol.getNombre(),
                    pol.getDireccion() + ", " + pol.getComuna(), cu.getBloque().getProfesional().nombreCompleto(),
                    cu.getBloque().getProfesional().getProfesion().getNombre(), cu.getBloque().getBox().getNombre(),
                    cu.getFecha(), cu.getHoraInicio(), Textos.capitalizar(Textos.fechaLarga(cu.getFecha())), cu.duracionMinutos(),
                    pendiente, pendiente
                            ? "Trae a tu primera atención: certificado de afiliación a FONASA, certificado de inscripción en tu CESFAM y tu RUT o documento de identidad."
                            : "Te enviaremos la confirmación y el recordatorio de tu hora por WhatsApp.");
        }
    }
}
