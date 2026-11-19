-- =====================================================================
-- Reglas de integridad que no se pueden expresar con CHECK
-- (dependen de otras tablas o de la fecha actual). MySQL 8.0.
-- Fuente: informe «Diseño de Datos del Proyecto», Anexo B (copiado tal cual).
-- Ejecutar después de 01_esquema_hermandad.sql
-- =====================================================================
SET NAMES utf8mb4;
USE hermandad_agenda;
DELIMITER $$

-- D-09 / RN-12: un profesional y un box no pueden tener bloques
-- superpuestos en el mismo día de la semana y período de vigencia.
CREATE TRIGGER trg_bloque_sin_superposicion_ins
BEFORE INSERT ON bloque_horario
FOR EACH ROW
BEGIN
  IF NEW.activo AND EXISTS (
    SELECT 1 FROM bloque_horario b
    WHERE b.activo
      AND b.dia_semana = NEW.dia_semana
      AND (b.id_profesional = NEW.id_profesional OR b.id_box = NEW.id_box)
      AND b.hora_inicio < NEW.hora_termino AND NEW.hora_inicio < b.hora_termino
      AND b.vigencia_desde <= NEW.vigencia_hasta AND NEW.vigencia_desde <= b.vigencia_hasta) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'El bloque se superpone con otro del mismo profesional o box (D-09).';
  END IF;
END$$

CREATE TRIGGER trg_bloque_sin_superposicion_upd
BEFORE UPDATE ON bloque_horario
FOR EACH ROW
BEGIN
  IF NEW.activo AND EXISTS (
    SELECT 1 FROM bloque_horario b
    WHERE b.id_bloque <> NEW.id_bloque
      AND b.activo
      AND b.dia_semana = NEW.dia_semana
      AND (b.id_profesional = NEW.id_profesional OR b.id_box = NEW.id_box)
      AND b.hora_inicio < NEW.hora_termino AND NEW.hora_inicio < b.hora_termino
      AND b.vigencia_desde <= NEW.vigencia_hasta AND NEW.vigencia_desde <= b.vigencia_hasta) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'El bloque se superpone con otro del mismo profesional o box (D-09).';
  END IF;
END$$

-- RN-05: un paciente menor de edad debe tener adulto responsable.
CREATE TRIGGER trg_paciente_menor_ins
BEFORE INSERT ON paciente
FOR EACH ROW
BEGIN
  IF TIMESTAMPDIFF(YEAR, NEW.fecha_nacimiento, CURDATE()) < 18
     AND NEW.id_adulto_responsable IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Paciente menor de edad sin adulto responsable (RN-05).';
  END IF;
END$$

CREATE TRIGGER trg_paciente_menor_upd
BEFORE UPDATE ON paciente
FOR EACH ROW
BEGIN
  IF TIMESTAMPDIFF(YEAR, NEW.fecha_nacimiento, CURDATE()) < 18
     AND NEW.id_adulto_responsable IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Paciente menor de edad sin adulto responsable (RN-05).';
  END IF;
END$$

-- RN-02: no se puede reservar un cupo bloqueado.
-- RN-01 / CA-RF15-2: la TENS/TONS solo reserva a pacientes habilitados;
-- la reserva en línea de un paciente sin verificación queda
-- PENDIENTE_VERIFICACION (RN-13).
CREATE TRIGGER trg_cita_reglas_ins
BEFORE INSERT ON cita
FOR EACH ROW
BEGIN
  DECLARE v_bloqueo INT;
  DECLARE v_resultado VARCHAR(20);
  SELECT id_bloqueo INTO v_bloqueo FROM cupo WHERE id_cupo = NEW.id_cupo;
  IF v_bloqueo IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El cupo está bloqueado (RN-02).';
  END IF;
  SELECT vr.resultado INTO v_resultado
    FROM verificacion_requisitos vr
    WHERE vr.id_paciente = NEW.id_paciente
    ORDER BY vr.fecha_verificacion DESC, vr.id_verificacion DESC
    LIMIT 1;
  IF NEW.canal_reserva = 'TENS' AND (v_resultado IS NULL OR v_resultado <> 'HABILITADO') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Paciente sin verificación FONASA/CESFAM: no se permite la reserva (CA-RF15-2).';
  END IF;
  IF NEW.canal_reserva = 'EN_LINEA' AND (v_resultado IS NULL OR v_resultado <> 'HABILITADO') THEN
    SET NEW.estado = 'PENDIENTE_VERIFICACION';
  END IF;
END$$

DELIMITER ;
