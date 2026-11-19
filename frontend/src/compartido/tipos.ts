export type Perfil = 'TENS' | 'ADMINISTRACION';

export interface Usuario {
  idUsuario: number;
  usuario: string;
  nombre: string;
  perfil: Perfil;
  idPoliclinicoBase: number | null;
  policlinicoBase: string | null;
}

export interface Box { id: number; nombre: string; activo: boolean }
export interface Policlinico { id: number; nombre: string; direccion: string; comuna: string; activo: boolean; boxes: Box[] }
export interface Profesion { id: number; nombre: string; duracionMin: number | null; duracionAlumnoMin: number | null }
export interface Profesional {
  id: number; nombres: string; apellidos: string; nombreCompleto: string; profesion: Profesion; duracionAtencion: number | null;
  esAlumnoPractica: boolean; credencial: { tituloProfesional: string | null; casaEstudios: string | null; registroPrestador: string | null };
  policlinicos: { id: number; nombre: string }[]; plazoPublicacionDias: number; activo: boolean; aviso: string | null;
}

export type EstadoCupo = 'BLOQUEADO' | 'RESERVADO' | 'NO_PUBLICADO' | 'DISPONIBLE' | 'PASADO';
export interface CupoVista {
  id: number; fecha: string; horaInicio: string; horaTermino: string; duracion: number; estado: EstadoCupo; habilitadoWeb: boolean;
  motivoBloqueo: string | null; idProfesional: number; profesional: string; idProfesion: number; profesion: string;
  idBox: number; box: string; idPoliclinico: number; policlinico: string; direccion: string; paciente: string | null;
}

export type EstadoCita = 'PENDIENTE_VERIFICACION' | 'RESERVADA' | 'ANULADA' | 'ASISTIDA' | 'INASISTIDA';
export type EstadoConfirmacion = 'SIN_ENVIAR' | 'ENVIADA' | 'NO_ENVIADA' | 'CONFIRMADA' | 'CONFIRMADA_TELEFONO' | 'SIN_RESPUESTA';

export interface Cita {
  id: number; estado: EstadoCita; estadoTexto: string; estadoConfirmacion: EstadoConfirmacion; canalReserva: 'TENS' | 'EN_LINEA';
  fecha: string; horaInicio: string; horaTermino: string; duracion: number; idCupo: number; idPaciente: number; paciente: string;
  nombrePila: string; iniciales: string; documento: string; telefono: string; adultoResponsable: string | null;
  relacionAdulto: string | null; telefonoAdulto: string | null; idProfesional: number; profesional: string; idProfesion: number;
  profesion: string; idBox: number; box: string; idPoliclinico: number; policlinico: string; direccion: string;
  idSerie: number | null; fechaReserva: string; origenAnulacion: string | null; motivoAnulacion: string | null;
  fechaAnulacion: string | null; fechaAsistencia: string | null;
}

export interface FilaCita { cita: Cita; requiereLlamada: boolean; avisadoA: string | null; tieneDocumentos: boolean }

export interface Verificacion {
  id: number; fechaVerificacion: string; cesfamInscripcion: string; fonasaVigente: boolean; inscritoCesfam: boolean;
  resultado: 'HABILITADO' | 'NO_CUMPLE'; observacion: string | null; registradaPor: string; perfilRegistro: string; descripcion: string;
}

export interface Paciente {
  id: number; tipoDocumento: 'RUT' | 'PASAPORTE' | 'OTRO'; numeroDocumento: string; documentoFormateado: string;
  nombres: string; apellidos: string; nombreCompleto: string; iniciales: string; fechaNacimiento: string; edad: number;
  esMenor: boolean; telefonoCelular: string; telefonoFormateado: string; correo: string | null; direccion: string;
  genero: string; nacionalidad: string;
  adultoResponsable: { id: number; tipoDocumento: string; numeroDocumento: string; documentoFormateado: string; nombreCompleto: string;
    telefono: string; telefonoFormateado: string; relacion: string } | null;
  canalRegistro: 'TENS' | 'EN_LINEA'; fechaAvisoMensajes: string; habilitado: boolean;
  estadoHabilitacion: 'HABILITADO' | 'NO_CUMPLE' | 'SIN_VERIFICACION'; verificacionVigente: Verificacion | null;
}

export interface Documento { id: number; tipo: 'CERTIFICADO_FONASA' | 'CERTIFICADO_CESFAM'; nombreArchivo: string; tipoMime: string; tamanoBytes: number; fechaCarga: string; idVerificacion: number | null }

export interface Inscripcion {
  id: number; posicion: number; idPaciente: number; paciente: string; iniciales: string; documento: string; telefono: string;
  idPoliclinico: number; policlinico: string; idProfesion: number; profesion: string; fechaInscripcion: string; fechaOrden: string;
  adelantada: boolean;
}

export interface AvisoCupo {
  id: number; idCupo: number; fecha: string; hora: string; profesion: string; profesional: string; box: string;
  idInscripcion: number; idPaciente: number; primera: string; telefono: string; mensaje: string;
}

export interface CeldaCupo { idCupo: number; horaInicio: string; horaTermino: string; estado: EstadoCupo; motivoBloqueo: string | null; cita: FilaCita | null }
export interface Columna { id: number; titulo: string; subtitulo: string; cupos: CeldaCupo[] }
export interface ResumenDia { citasHoy: number; confirmadas: number; porLlamar: number; sinRespuesta: number; enListaEspera: number; pendientesVerificacion: number }
export interface AgendaDia { fecha: string; idPoliclinico: number | null; vista: VistaAgenda; resumen: ResumenDia; citas: FilaCita[]; columnas: Columna[] }
export type VistaAgenda = 'LISTA' | 'POR_PROFESIONAL' | 'POR_BOX';

export interface MensajeCita { id: number; tipo: string; plantilla: string; destinatario: string; telefono: string; estado: string; enviadoPor: string; fechaProgramada: string; fechaEnvio: string | null; texto: string }
export interface DetalleCita { fila: FilaCita; mensajes: MensajeCita[]; historial: { fechaHora: string; texto: string }[] }

export interface Parametro { clave: string; valor: string | null; tipoDato: string; descripcion: string }
export interface CitaAfectada { idCita: number; idCupo: number; paciente: string; documento: string; telefono: string; fecha: string; hora: string }
