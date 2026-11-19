-- =====================================================================
-- Sistema de Agendamiento y Confirmación de Atenciones
-- Instituto de Caridad Hermandad de Dolores
-- Modelo físico · MySQL 8.0 (InnoDB, utf8mb4)
-- Equipo: Nicolás Bravo y José Tomás Amat · Ingeniería de Software UDD
-- Fuente: informe «Diseño de Datos del Proyecto», Anexo A (copiado tal cual).
-- Orden de creación: catálogos → agenda → pacientes → citas →
-- lista de espera → mensajería → parámetros → auditoría.
-- Datos de pacientes: solo ficticios. El sistema no guarda información
-- clínica (RES-08).
-- =====================================================================
SET NAMES utf8mb4;
DROP DATABASE IF EXISTS hermandad_agenda;
CREATE DATABASE hermandad_agenda
  CHARACTER SET utf8mb4 COLLATE utf8mb4_spanish_ci;
USE hermandad_agenda;

-- ---------------------------------------------------------------------
-- 1. Seguridad y usuarios (RNF-04)
-- ---------------------------------------------------------------------
CREATE TABLE perfil (
  id_perfil TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(40) NOT NULL,
  descripcion VARCHAR(200) NULL,
  PRIMARY KEY (id_perfil),
  UNIQUE KEY uq_perfil_nombre (nombre)
) ENGINE=InnoDB COMMENT='Perfiles de acceso: Administración y TENS/TONS (RNF-04).';

-- ---------------------------------------------------------------------
-- 2. Policlínicos, box y profesionales (CU-01)
-- ---------------------------------------------------------------------
CREATE TABLE policlinico (
  id_policlinico SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(80) NOT NULL,
  direccion VARCHAR(150) NOT NULL,
  comuna VARCHAR(60) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_policlinico),
  UNIQUE KEY uq_policlinico_nombre (nombre)
) ENGINE=InnoDB COMMENT='RF-01. Los 6 policlínicos de la Hermandad.';

CREATE TABLE usuario (
  id_usuario INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_perfil TINYINT UNSIGNED NOT NULL,
  id_policlinico SMALLINT UNSIGNED NULL COMMENT 'Policlínico base de la TENS/TONS; NULL para Administración',
  nombre_completo VARCHAR(120) NOT NULL,
  nombre_usuario VARCHAR(40) NOT NULL,
  hash_contrasena VARCHAR(255) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uq_usuario_nombre_usuario (nombre_usuario),
  KEY ix_usuario_perfil (id_perfil),
  KEY ix_usuario_policlinico (id_policlinico),
  CONSTRAINT fk_usuario_perfil FOREIGN KEY (id_perfil) REFERENCES perfil (id_perfil),
  CONSTRAINT fk_usuario_policlinico FOREIGN KEY (id_policlinico) REFERENCES policlinico (id_policlinico)
) ENGINE=InnoDB COMMENT='Personal que usa el sistema (RNF-04). La contraseña se guarda solo como hash.';

CREATE TABLE box (
  id_box SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_policlinico SMALLINT UNSIGNED NOT NULL,
  nombre VARCHAR(30) NOT NULL COMMENT 'Ej.: Box 1',
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_box),
  UNIQUE KEY uq_box_policlinico_nombre (id_policlinico, nombre),
  CONSTRAINT fk_box_policlinico FOREIGN KEY (id_policlinico) REFERENCES policlinico (id_policlinico)
) ENGINE=InnoDB COMMENT='RF-01, RF-40. Hasta 6 box en paralelo por policlínico.';

CREATE TABLE profesion (
  id_profesion SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(60) NOT NULL,
  duracion_min SMALLINT UNSIGNED NULL COMMENT 'Minutos por paciente (RN-04). NULL = por definir: no se generan cupos',
  duracion_alumno_min SMALLINT UNSIGNED NULL COMMENT 'Minutos cuando atiende un alumno en práctica (D-08)',
  PRIMARY KEY (id_profesion),
  UNIQUE KEY uq_profesion_nombre (nombre),
  CONSTRAINT ck_profesion_duracion CHECK (duracion_min IS NULL OR duracion_min BETWEEN 5 AND 240),
  CONSTRAINT ck_profesion_duracion_alumno CHECK (duracion_alumno_min IS NULL OR duracion_alumno_min BETWEEN 5 AND 240)
) ENGINE=InnoDB COMMENT='RF-05, RN-04. Duraciones según planilla «Estándar de atención por profesional».';

CREATE TABLE profesional (
  id_profesional INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_profesion SMALLINT UNSIGNED NOT NULL,
  nombres VARCHAR(80) NOT NULL,
  apellidos VARCHAR(80) NOT NULL,
  es_alumno_practica BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'D-08: usa la duración de alumnos',
  titulo_profesional VARCHAR(120) NULL COMMENT 'Credencial opcional (RF-03)',
  casa_estudios VARCHAR(120) NULL COMMENT 'Credencial opcional (RF-03)',
  registro_prestador VARCHAR(40) NULL COMMENT 'N.º de registro en la Superintendencia de Salud (RF-03)',
  plazo_publicacion_dias SMALLINT UNSIGNED NOT NULL DEFAULT 7 COMMENT 'Días hacia adelante que se muestran al público (RF-08, RN-08; validado 13/10)',
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_profesional),
  KEY ix_profesional_profesion (id_profesion),
  CONSTRAINT fk_profesional_profesion FOREIGN KEY (id_profesion) REFERENCES profesion (id_profesion),
  CONSTRAINT ck_profesional_plazo CHECK (plazo_publicacion_dias BETWEEN 1 AND 180)
) ENGINE=InnoDB COMMENT='RF-02, RF-03, RF-08.';

CREATE TABLE profesional_policlinico (
  id_profesional INT UNSIGNED NOT NULL,
  id_policlinico SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (id_profesional, id_policlinico),
  KEY ix_pp_policlinico (id_policlinico),
  CONSTRAINT fk_pp_profesional FOREIGN KEY (id_profesional) REFERENCES profesional (id_profesional),
  CONSTRAINT fk_pp_policlinico FOREIGN KEY (id_policlinico) REFERENCES policlinico (id_policlinico)
) ENGINE=InnoDB COMMENT='RF-02. Un profesional atiende en uno o más policlínicos (N:M).';

-- ---------------------------------------------------------------------
-- 3. Agenda de cupos (CU-02)
-- ---------------------------------------------------------------------
CREATE TABLE bloque_horario (
  id_bloque INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_profesional INT UNSIGNED NOT NULL,
  id_box SMALLINT UNSIGNED NOT NULL,
  dia_semana TINYINT UNSIGNED NOT NULL COMMENT '1 = lunes … 7 = domingo',
  hora_inicio TIME NOT NULL,
  hora_termino TIME NOT NULL,
  vigencia_desde DATE NOT NULL COMMENT 'La agenda se arma por semestre (13/10)',
  vigencia_hasta DATE NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  id_usuario INT UNSIGNED NOT NULL COMMENT 'Administración que lo registró (RN-03)',
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_bloque),
  KEY ix_bloque_profesional (id_profesional, dia_semana),
  KEY ix_bloque_box (id_box, dia_semana),
  KEY ix_bloque_usuario (id_usuario),
  CONSTRAINT fk_bloque_profesional FOREIGN KEY (id_profesional) REFERENCES profesional (id_profesional),
  CONSTRAINT fk_bloque_box FOREIGN KEY (id_box) REFERENCES box (id_box),
  CONSTRAINT fk_bloque_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_bloque_dia CHECK (dia_semana BETWEEN 1 AND 7),
  CONSTRAINT ck_bloque_horas CHECK (hora_termino > hora_inicio),
  CONSTRAINT ck_bloque_vigencia CHECK (vigencia_hasta >= vigencia_desde)
) ENGINE=InnoDB COMMENT='RF-04, RN-03. Bloques superpuestos se impiden con disparador (D-09, archivo 02).';

CREATE TABLE bloqueo (
  id_bloqueo INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_usuario INT UNSIGNED NOT NULL COMMENT 'Solo Administración (D-12)',
  motivo VARCHAR(150) NOT NULL COMMENT 'Ej.: taller de salud comunitaria',
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_bloqueo),
  KEY ix_bloqueo_usuario (id_usuario),
  CONSTRAINT fk_bloqueo_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB COMMENT='RF-07. Un bloqueo agrupa uno o más cupos (un cupo o un rango horario).';

CREATE TABLE cupo (
  id_cupo BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_bloque INT UNSIGNED NOT NULL,
  fecha DATE NOT NULL,
  hora_inicio TIME NOT NULL,
  hora_termino TIME NOT NULL COMMENT 'Se guarda porque la duración de la profesión puede cambiar después',
  habilitado_web BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Se ofrece en la reserva en línea (RF-37, D-01)',
  id_bloqueo INT UNSIGNED NULL COMMENT 'NULL = no bloqueado (RF-07)',
  PRIMARY KEY (id_cupo),
  UNIQUE KEY uq_cupo_bloque_fecha_hora (id_bloque, fecha, hora_inicio),
  KEY ix_cupo_fecha (fecha, hora_inicio),
  KEY ix_cupo_bloqueo (id_bloqueo),
  CONSTRAINT fk_cupo_bloque FOREIGN KEY (id_bloque) REFERENCES bloque_horario (id_bloque),
  CONSTRAINT fk_cupo_bloqueo FOREIGN KEY (id_bloqueo) REFERENCES bloqueo (id_bloqueo),
  CONSTRAINT ck_cupo_horas CHECK (hora_termino > hora_inicio)
) ENGINE=InnoDB COMMENT='RF-06. Disponible, reservado, bloqueado o no publicado se derivan (no se guardan dos veces).';

-- ---------------------------------------------------------------------
-- 4. Pacientes (CU-03)
-- ---------------------------------------------------------------------
CREATE TABLE adulto_responsable (
  id_adulto INT UNSIGNED NOT NULL AUTO_INCREMENT,
  tipo_documento ENUM('RUT','PASAPORTE','OTRO') NOT NULL DEFAULT 'RUT',
  numero_documento VARCHAR(20) NOT NULL,
  nombre_completo VARCHAR(120) NOT NULL,
  telefono VARCHAR(20) NOT NULL COMMENT 'Recibe confirmaciones y recordatorios (CAM-21, D-07)',
  PRIMARY KEY (id_adulto),
  UNIQUE KEY uq_adulto_documento (tipo_documento, numero_documento)
) ENGINE=InnoDB COMMENT='RF-12, RN-05. Una persona puede ser responsable de varios pacientes.';

CREATE TABLE paciente (
  id_paciente INT UNSIGNED NOT NULL AUTO_INCREMENT,
  tipo_documento ENUM('RUT','PASAPORTE','OTRO') NOT NULL DEFAULT 'RUT' COMMENT 'D-14: pacientes extranjeros sin RUT',
  numero_documento VARCHAR(20) NOT NULL,
  nombres VARCHAR(80) NOT NULL,
  apellidos VARCHAR(80) NOT NULL,
  fecha_nacimiento DATE NOT NULL COMMENT 'También se usa para identificarse en línea (RF-35)',
  telefono_celular VARCHAR(20) NOT NULL,
  correo VARCHAR(120) NULL COMMENT 'Opcional (RN-09)',
  direccion VARCHAR(150) NOT NULL,
  genero VARCHAR(30) NOT NULL COMMENT 'Categorías según la ficha de ingreso de la institución',
  nacionalidad VARCHAR(60) NOT NULL,
  id_adulto_responsable INT UNSIGNED NULL COMMENT 'Obligatorio si es menor de edad (RN-05)',
  relacion_adulto VARCHAR(40) NULL COMMENT 'Relación del adulto con el paciente (RF-12)',
  fecha_aviso_mensajes DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Cuándo se le informó al paciente (o a su adulto responsable) que recibirá la confirmación y el recordatorio por WhatsApp; respaldo del aviso ante la política de aceptación de WhatsApp (ajuste 22/10)',
  canal_registro ENUM('TENS','EN_LINEA') NOT NULL COMMENT 'RF-10 o RF-36',
  id_usuario_registro INT UNSIGNED NULL COMMENT 'NULL si se registró en línea',
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_paciente),
  UNIQUE KEY uq_paciente_documento (tipo_documento, numero_documento),
  KEY ix_paciente_apellidos (apellidos, nombres),
  KEY ix_paciente_adulto (id_adulto_responsable),
  KEY ix_paciente_usuario (id_usuario_registro),
  CONSTRAINT fk_paciente_adulto FOREIGN KEY (id_adulto_responsable) REFERENCES adulto_responsable (id_adulto),
  CONSTRAINT fk_paciente_usuario FOREIGN KEY (id_usuario_registro) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_paciente_relacion CHECK ((id_adulto_responsable IS NULL AND relacion_adulto IS NULL)
    OR (id_adulto_responsable IS NOT NULL AND relacion_adulto IS NOT NULL)),
  CONSTRAINT ck_paciente_canal CHECK (canal_registro = 'EN_LINEA' OR id_usuario_registro IS NOT NULL)
) ENGINE=InnoDB COMMENT='RF-09 a RF-11, RF-36. Sin información clínica (RES-08).';

CREATE TABLE verificacion_requisitos (
  id_verificacion INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_paciente INT UNSIGNED NOT NULL,
  id_usuario INT UNSIGNED NOT NULL COMMENT 'TENS/TONS que revisó los certificados',
  fecha_verificacion DATE NOT NULL,
  cesfam_inscripcion VARCHAR(100) NOT NULL,
  fonasa_vigente BOOLEAN NOT NULL,
  inscrito_cesfam BOOLEAN NOT NULL,
  resultado ENUM('HABILITADO','NO_CUMPLE') NOT NULL,
  observacion VARCHAR(300) NULL,
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_verificacion),
  KEY ix_verif_paciente (id_paciente, fecha_verificacion),
  KEY ix_verif_usuario (id_usuario),
  CONSTRAINT fk_verif_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente),
  CONSTRAINT fk_verif_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_verif_resultado CHECK (resultado = 'NO_CUMPLE' OR (fonasa_vigente = TRUE AND inscrito_cesfam = TRUE))
) ENGINE=InnoDB COMMENT='RF-13, RN-01. Se guarda cada verificación; vale la más reciente.';

CREATE TABLE documento_adjunto (
  id_documento INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_paciente INT UNSIGNED NOT NULL,
  tipo ENUM('CERTIFICADO_FONASA','CERTIFICADO_CESFAM') NOT NULL,
  nombre_archivo VARCHAR(150) NOT NULL,
  tipo_mime VARCHAR(60) NOT NULL,
  tamano_bytes INT UNSIGNED NOT NULL,
  ruta_almacenamiento VARCHAR(255) NOT NULL COMMENT 'El archivo se guarda fuera de la base; aquí solo su ubicación',
  fecha_carga DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  id_verificacion INT UNSIGNED NULL COMMENT 'Verificación en que la TENS/TONS lo revisó',
  PRIMARY KEY (id_documento),
  KEY ix_doc_paciente (id_paciente),
  KEY ix_doc_verificacion (id_verificacion),
  CONSTRAINT fk_doc_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente),
  CONSTRAINT fk_doc_verificacion FOREIGN KEY (id_verificacion) REFERENCES verificacion_requisitos (id_verificacion),
  CONSTRAINT ck_doc_mime CHECK (tipo_mime IN ('application/pdf','image/jpeg','image/png')),
  CONSTRAINT ck_doc_tamano CHECK (tamano_bytes > 0)
) ENGINE=InnoDB COMMENT='RF-41. Certificados subidos en la reserva en línea.';

-- ---------------------------------------------------------------------
-- 5. Citas, series y asistencia (CU-04, CU-06, CU-08)
-- ---------------------------------------------------------------------
CREATE TABLE serie_sesiones (
  id_serie INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_paciente INT UNSIGNED NOT NULL,
  id_profesional INT UNSIGNED NOT NULL,
  cantidad_sesiones TINYINT UNSIGNED NOT NULL COMMENT 'Las que indica el profesional (RN-11, validado 13/10)',
  dia_semana TINYINT UNSIGNED NOT NULL,
  hora TIME NOT NULL,
  fecha_primera DATE NOT NULL,
  id_usuario INT UNSIGNED NOT NULL COMMENT 'La serie la reserva la TENS/TONS',
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_serie),
  KEY ix_serie_paciente (id_paciente),
  KEY ix_serie_profesional (id_profesional),
  KEY ix_serie_usuario (id_usuario),
  CONSTRAINT fk_serie_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente),
  CONSTRAINT fk_serie_profesional FOREIGN KEY (id_profesional) REFERENCES profesional (id_profesional),
  CONSTRAINT fk_serie_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_serie_cantidad CHECK (cantidad_sesiones BETWEEN 2 AND 30),
  CONSTRAINT ck_serie_dia CHECK (dia_semana BETWEEN 1 AND 7)
) ENGINE=InnoDB COMMENT='RF-16. Cada sesión es una cita propia; la serie las agrupa.';

CREATE TABLE cita (
  id_cita BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_paciente INT UNSIGNED NOT NULL,
  id_cupo BIGINT UNSIGNED NOT NULL,
  id_serie INT UNSIGNED NULL,
  canal_reserva ENUM('TENS','EN_LINEA') NOT NULL,
  estado ENUM('PENDIENTE_VERIFICACION','RESERVADA','ANULADA','ASISTIDA','INASISTIDA') NOT NULL DEFAULT 'RESERVADA',
  estado_confirmacion ENUM('SIN_ENVIAR','ENVIADA','NO_ENVIADA','CONFIRMADA','CONFIRMADA_TELEFONO','SIN_RESPUESTA') NOT NULL DEFAULT 'SIN_ENVIAR',
  id_usuario_reserva INT UNSIGNED NULL COMMENT 'NULL si se reservó en línea',
  fecha_reserva DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  origen_anulacion ENUM('PACIENTE_MENSAJE','TENS','ADMINISTRACION') NULL,
  motivo_anulacion VARCHAR(200) NULL,
  fecha_anulacion DATETIME NULL,
  id_usuario_anulacion INT UNSIGNED NULL,
  fecha_asistencia DATETIME NULL COMMENT 'Momento en que se registró asistencia o inasistencia (RF-22)',
  id_usuario_asistencia INT UNSIGNED NULL,
  id_cupo_activo BIGINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN estado <> 'ANULADA' THEN id_cupo END) STORED
    COMMENT 'Garantiza una sola cita vigente por cupo (RN-02, CA-RF15-3)',
  PRIMARY KEY (id_cita),
  UNIQUE KEY uq_cita_cupo_activo (id_cupo_activo),
  KEY ix_cita_cupo (id_cupo),
  KEY ix_cita_paciente (id_paciente, estado),
  KEY ix_cita_serie (id_serie),
  KEY ix_cita_usuario_reserva (id_usuario_reserva),
  KEY ix_cita_usuario_anulacion (id_usuario_anulacion),
  KEY ix_cita_usuario_asistencia (id_usuario_asistencia),
  CONSTRAINT fk_cita_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente),
  CONSTRAINT fk_cita_cupo FOREIGN KEY (id_cupo) REFERENCES cupo (id_cupo),
  CONSTRAINT fk_cita_serie FOREIGN KEY (id_serie) REFERENCES serie_sesiones (id_serie),
  CONSTRAINT fk_cita_usuario_reserva FOREIGN KEY (id_usuario_reserva) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_cita_usuario_anulacion FOREIGN KEY (id_usuario_anulacion) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_cita_usuario_asistencia FOREIGN KEY (id_usuario_asistencia) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_cita_canal CHECK (canal_reserva = 'EN_LINEA' OR id_usuario_reserva IS NOT NULL),
  CONSTRAINT ck_cita_anulacion CHECK ((estado = 'ANULADA') = (fecha_anulacion IS NOT NULL AND origen_anulacion IS NOT NULL)),
  CONSTRAINT ck_cita_asistencia CHECK ((estado IN ('ASISTIDA','INASISTIDA')) = (fecha_asistencia IS NOT NULL))
) ENGINE=InnoDB COMMENT='RF-15, RF-17, RF-22, RF-23, RF-34. Las citas no se borran: cambian de estado (D-17).';

-- ---------------------------------------------------------------------
-- 6. Lista de espera (CU-04: RF-25, RF-26, RF-42, RF-43)
-- ---------------------------------------------------------------------
CREATE TABLE inscripcion_espera (
  id_inscripcion INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_paciente INT UNSIGNED NOT NULL,
  id_policlinico SMALLINT UNSIGNED NOT NULL,
  id_profesion SMALLINT UNSIGNED NOT NULL COMMENT 'Atención solicitada (RF-25)',
  fecha_inscripcion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Orden de llegada (RN-06)',
  fecha_orden DATETIME NOT NULL COMMENT 'Ordena la lista; cambia solo si la TENS adelanta (RF-42)',
  estado ENUM('EN_ESPERA','RETIRADA') NOT NULL DEFAULT 'EN_ESPERA',
  motivo_retiro ENUM('HORA_ASIGNADA','DESISTE','OTRO') NULL COMMENT 'D-05, RF-43',
  id_cita_asignada BIGINT UNSIGNED NULL,
  id_usuario INT UNSIGNED NOT NULL,
  PRIMARY KEY (id_inscripcion),
  KEY ix_espera_lista (id_policlinico, id_profesion, estado, fecha_orden),
  KEY ix_espera_paciente (id_paciente),
  KEY ix_espera_profesion (id_profesion),
  KEY ix_espera_cita (id_cita_asignada),
  KEY ix_espera_usuario (id_usuario),
  CONSTRAINT fk_espera_paciente FOREIGN KEY (id_paciente) REFERENCES paciente (id_paciente),
  CONSTRAINT fk_espera_policlinico FOREIGN KEY (id_policlinico) REFERENCES policlinico (id_policlinico),
  CONSTRAINT fk_espera_profesion FOREIGN KEY (id_profesion) REFERENCES profesion (id_profesion),
  CONSTRAINT fk_espera_cita FOREIGN KEY (id_cita_asignada) REFERENCES cita (id_cita),
  CONSTRAINT fk_espera_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_espera_retiro CHECK ((estado = 'RETIRADA') = (motivo_retiro IS NOT NULL)),
  CONSTRAINT ck_espera_cita CHECK (id_cita_asignada IS NULL OR motivo_retiro = 'HORA_ASIGNADA')
) ENGINE=InnoDB COMMENT='Lista de espera por policlínico y atención.';

CREATE TABLE movimiento_espera (
  id_movimiento INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_inscripcion INT UNSIGNED NOT NULL,
  tipo ENUM('INSCRIPCION','ADELANTO','RETIRO') NOT NULL,
  motivo VARCHAR(200) NULL,
  id_usuario INT UNSIGNED NOT NULL,
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_movimiento),
  KEY ix_mov_inscripcion (id_inscripcion, fecha_hora),
  KEY ix_mov_usuario (id_usuario),
  CONSTRAINT fk_mov_inscripcion FOREIGN KEY (id_inscripcion) REFERENCES inscripcion_espera (id_inscripcion),
  CONSTRAINT fk_mov_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_mov_motivo CHECK (tipo = 'INSCRIPCION' OR (motivo IS NOT NULL AND CHAR_LENGTH(motivo) > 0))
) ENGINE=InnoDB COMMENT='RF-42, RF-43. Quién adelantó o retiró, cuándo y por qué (CA-RF42-1).';

CREATE TABLE aviso_cupo_liberado (
  id_aviso INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_cupo BIGINT UNSIGNED NOT NULL,
  id_cita_anulada BIGINT UNSIGNED NOT NULL,
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atendido BOOLEAN NOT NULL DEFAULT FALSE,
  id_usuario INT UNSIGNED NULL COMMENT 'TENS/TONS que lo atendió',
  PRIMARY KEY (id_aviso),
  KEY ix_aviso_cupo (id_cupo),
  KEY ix_aviso_cita (id_cita_anulada),
  KEY ix_aviso_usuario (id_usuario),
  CONSTRAINT fk_aviso_cupo FOREIGN KEY (id_cupo) REFERENCES cupo (id_cupo),
  CONSTRAINT fk_aviso_cita FOREIGN KEY (id_cita_anulada) REFERENCES cita (id_cita),
  CONSTRAINT fk_aviso_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_aviso_atendido CHECK (atendido = FALSE OR id_usuario IS NOT NULL)
) ENGINE=InnoDB COMMENT='RF-26. Aviso a la TENS/TONS cuando se libera un cupo con pacientes en espera.';

-- ---------------------------------------------------------------------
-- 7. Mensajería y confirmación (CU-05, CU-06)
-- ---------------------------------------------------------------------
CREATE TABLE plantilla_mensaje (
  id_plantilla SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tipo ENUM('CONFIRMACION','RECORDATORIO','REFUERZO','ANULACION') NOT NULL,
  nombre_proveedor VARCHAR(80) NOT NULL COMMENT 'Nombre de la plantilla aprobada en WhatsApp',
  texto VARCHAR(1000) NOT NULL COMMENT 'Lenguaje simple y sin información clínica (RNF-02, RNF-06)',
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_plantilla),
  UNIQUE KEY uq_plantilla_nombre (nombre_proveedor)
) ENGINE=InnoDB COMMENT='RF-19, RF-20, RF-21, RF-38.';

CREATE TABLE mensaje (
  id_mensaje BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_cita BIGINT UNSIGNED NOT NULL,
  id_plantilla SMALLINT UNSIGNED NOT NULL,
  destinatario ENUM('PACIENTE','ADULTO_RESPONSABLE') NOT NULL,
  telefono_destino VARCHAR(20) NOT NULL COMMENT 'Número al que se envió (se conserva aunque el paciente cambie su teléfono)',
  canal ENUM('WHATSAPP') NOT NULL DEFAULT 'WHATSAPP' COMMENT 'Validado 13/10 (CAM-17)',
  enviado_por ENUM('SISTEMA','TENS') NOT NULL DEFAULT 'SISTEMA',
  id_usuario INT UNSIGNED NULL COMMENT 'TENS/TONS que reenvió (RF-38)',
  fecha_programada DATETIME NOT NULL,
  fecha_envio DATETIME NULL,
  estado_entrega ENUM('PROGRAMADO','ENVIADO','ENTREGADO','LEIDO','FALLIDO') NOT NULL DEFAULT 'PROGRAMADO',
  id_externo VARCHAR(100) NULL COMMENT 'Identificador del proveedor de mensajería',
  PRIMARY KEY (id_mensaje),
  KEY ix_mensaje_cita (id_cita),
  KEY ix_mensaje_pendientes (estado_entrega, fecha_programada),
  KEY ix_mensaje_plantilla (id_plantilla),
  KEY ix_mensaje_usuario (id_usuario),
  CONSTRAINT fk_mensaje_cita FOREIGN KEY (id_cita) REFERENCES cita (id_cita),
  CONSTRAINT fk_mensaje_plantilla FOREIGN KEY (id_plantilla) REFERENCES plantilla_mensaje (id_plantilla),
  CONSTRAINT fk_mensaje_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_mensaje_envio CHECK (estado_entrega = 'PROGRAMADO' OR fecha_envio IS NOT NULL),
  CONSTRAINT ck_mensaje_origen CHECK (enviado_por = 'SISTEMA' OR id_usuario IS NOT NULL)
) ENGINE=InnoDB COMMENT='RF-19, RF-21, RF-38. Historial de cada mensaje enviado.';

CREATE TABLE respuesta_mensaje (
  id_respuesta BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_mensaje BIGINT UNSIGNED NOT NULL,
  respuesta ENUM('ASISTIRA','NO_ASISTIRA') NOT NULL,
  fecha_hora DATETIME NOT NULL,
  PRIMARY KEY (id_respuesta),
  KEY ix_respuesta_mensaje (id_mensaje),
  CONSTRAINT fk_respuesta_mensaje FOREIGN KEY (id_mensaje) REFERENCES mensaje (id_mensaje)
) ENGINE=InnoDB COMMENT='RF-21, RF-33. «No podré asistir» anula la cita (RN-07).';

CREATE TABLE llamada_confirmacion (
  id_llamada BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_cita BIGINT UNSIGNED NOT NULL,
  id_usuario INT UNSIGNED NOT NULL COMMENT 'TENS/TONS que llamó',
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resultado ENUM('CONFIRMO','NO_ASISTIRA','NO_CONTESTO') NOT NULL,
  comentario VARCHAR(500) NULL,
  PRIMARY KEY (id_llamada),
  KEY ix_llamada_cita (id_cita),
  KEY ix_llamada_usuario (id_usuario),
  CONSTRAINT fk_llamada_cita FOREIGN KEY (id_cita) REFERENCES cita (id_cita),
  CONSTRAINT fk_llamada_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB COMMENT='RF-39. Confirmación telefónica con comentario (CAM-18, D-15).';

-- ---------------------------------------------------------------------
-- 8. Parámetros configurables (D-01, D-02, D-04, D-13)
-- ---------------------------------------------------------------------
CREATE TABLE parametro_sistema (
  clave VARCHAR(50) NOT NULL,
  valor VARCHAR(100) NULL COMMENT 'NULL = no configurado / desactivado',
  tipo_dato ENUM('ENTERO','PORCENTAJE','HORAS','TEXTO','BOOLEANO') NOT NULL,
  descripcion VARCHAR(250) NOT NULL,
  id_usuario INT UNSIGNED NULL COMMENT 'Administración que lo modificó por última vez',
  fecha_modificacion DATETIME NULL,
  PRIMARY KEY (clave),
  KEY ix_parametro_usuario (id_usuario),
  CONSTRAINT fk_parametro_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB COMMENT='Valores que define la Administración: % web, tope, refuerzos, umbral.';

CREATE TABLE categoria_etaria (
  id_categoria TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(40) NOT NULL,
  edad_desde TINYINT UNSIGNED NOT NULL,
  edad_hasta TINYINT UNSIGNED NULL COMMENT 'NULL = sin límite superior',
  PRIMARY KEY (id_categoria),
  UNIQUE KEY uq_categoria_nombre (nombre),
  CONSTRAINT ck_categoria_rango CHECK (edad_hasta IS NULL OR edad_hasta >= edad_desde)
) ENGINE=InnoDB COMMENT='RF-30. Tramos de edad que define la Administración (D-13).';

-- ---------------------------------------------------------------------
-- 9. Auditoría (RNF-05)
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
  id_auditoria BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_usuario INT UNSIGNED NULL COMMENT 'NULL = acción del sistema o del paciente en línea',
  fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  accion ENUM('CREAR','MODIFICAR','ANULAR','VERIFICAR','ADELANTAR','RETIRAR','BLOQUEAR') NOT NULL,
  entidad VARCHAR(40) NOT NULL COMMENT 'Tabla afectada',
  id_registro BIGINT UNSIGNED NOT NULL,
  detalle VARCHAR(500) NULL,
  PRIMARY KEY (id_auditoria),
  KEY ix_auditoria_entidad (entidad, id_registro),
  KEY ix_auditoria_usuario (id_usuario, fecha_hora),
  CONSTRAINT fk_auditoria_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB COMMENT='RNF-05. Usuario, fecha y hora de reservas, anulaciones y verificaciones.';
