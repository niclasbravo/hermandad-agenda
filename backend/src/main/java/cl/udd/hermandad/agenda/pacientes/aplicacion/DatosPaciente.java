package cl.udd.hermandad.agenda.pacientes.aplicacion;

import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;

import java.time.LocalDate;

public record DatosPaciente(TipoDocumento tipoDocumento, String numeroDocumento, String nombres, String apellidos,
                            LocalDate fechaNacimiento, String telefonoCelular, String correo, String direccion,
                            String genero, String nacionalidad, DatosAdulto adulto) {

    public record DatosAdulto(TipoDocumento tipoDocumento, String numeroDocumento, String nombreCompleto,
                              String telefono, String relacion) {
        public boolean vacio() {
            return (numeroDocumento == null || numeroDocumento.isBlank()) && (nombreCompleto == null || nombreCompleto.isBlank())
                    && (telefono == null || telefono.isBlank());
        }
    }
}
