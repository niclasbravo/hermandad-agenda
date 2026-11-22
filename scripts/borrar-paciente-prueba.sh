#!/usr/bin/env bash
# Borra UN paciente de prueba (por RUT/documento) con sus citas, mensajes, respuestas, llamadas, listas de espera
# y documentos, para poder repetir la reserva en línea y la confirmación por WhatsApp en las demos.
# Los cupos quedan libres solos (su estado se deriva de las citas).
#
#   scripts/borrar-paciente-prueba.sh 20.431.749-6                      # solo muestra lo que encontró (local)
#   scripts/borrar-paciente-prueba.sh 20.431.749-6 --borrar             # borra (local)
#   scripts/borrar-paciente-prueba.sh 20.431.749-6 --nube               # muestra (servidor en la nube)
#   scripts/borrar-paciente-prueba.sh 20.431.749-6 --nube --borrar      # borra (servidor en la nube)
#
# Solo para datos de prueba: no deja respaldo. Los archivos de certificados ya subidos quedan en disco.
# Variables opcionales para --nube: SERVIDOR (ubuntu@100.59.97.56) y LLAVE (~/.ssh/labsuser.pem).
set -euo pipefail

DOC_ENTRADA="${1:?Uso: scripts/borrar-paciente-prueba.sh RUT [--nube] [--borrar]}"
shift
NUBE=0; BORRAR=0
for a in "$@"; do
  case "$a" in
    --nube) NUBE=1 ;;
    --borrar) BORRAR=1 ;;
    *) echo "Opción desconocida: $a"; exit 1 ;;
  esac
done

# Normaliza: sin puntos ni espacios, K mayúscula ("20.431.749-6" -> "20431749-6").
DOC=$(echo "$DOC_ENTRADA" | tr -d '. ' | tr 'k' 'K')
[[ "$DOC" =~ ^[0-9A-Z-]{3,20}$ ]] || { echo "Documento no válido: $DOC_ENTRADA"; exit 1; }

read -r -d '' SQL_VER <<EOF || true
SET NAMES utf8mb4;
SET @p := (SELECT id_paciente FROM paciente WHERE numero_documento = '$DOC');
SELECT IFNULL(@p, 'no existe') AS paciente,
       (SELECT CONCAT(nombres, ' ', apellidos) FROM paciente WHERE id_paciente = @p) AS nombre,
       (SELECT COUNT(*) FROM cita WHERE id_paciente = @p) AS citas,
       (SELECT COUNT(*) FROM mensaje m JOIN cita c ON c.id_cita = m.id_cita WHERE c.id_paciente = @p) AS mensajes,
       (SELECT COUNT(*) FROM inscripcion_espera WHERE id_paciente = @p) AS en_lista_espera;
EOF

read -r -d '' SQL_BORRAR <<EOF || true
SET NAMES utf8mb4;
START TRANSACTION;
SET @p := (SELECT id_paciente FROM paciente WHERE numero_documento = '$DOC');
DELETE r FROM respuesta_mensaje r JOIN mensaje m ON m.id_mensaje = r.id_mensaje JOIN cita c ON c.id_cita = m.id_cita WHERE c.id_paciente = @p;
DELETE m FROM mensaje m JOIN cita c ON c.id_cita = m.id_cita WHERE c.id_paciente = @p;
DELETE l FROM llamada_confirmacion l JOIN cita c ON c.id_cita = l.id_cita WHERE c.id_paciente = @p;
DELETE a FROM aviso_cupo_liberado a JOIN cita c ON c.id_cita = a.id_cita_anulada WHERE c.id_paciente = @p;
DELETE FROM inscripcion_espera WHERE id_paciente = @p;
DELETE FROM cita WHERE id_paciente = @p;
DELETE FROM verificacion_requisitos WHERE id_paciente = @p;
DELETE FROM documento_adjunto WHERE id_paciente = @p;
DELETE FROM serie_sesiones WHERE id_paciente = @p;
DELETE FROM paciente WHERE id_paciente = @p;
COMMIT;
SELECT IF(@p IS NULL, 'No había paciente con ese documento', CONCAT('Paciente ', @p, ' borrado')) AS resultado;
EOF

if [ "$BORRAR" = 1 ]; then SQL="$SQL_BORRAR"; else SQL="$SQL_VER"; fi
MYSQL_CMD='mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" hermandad_agenda'

if [ "$NUBE" = 1 ]; then
  SERVIDOR="${SERVIDOR:-ubuntu@100.59.97.56}"
  LLAVE="${LLAVE:-$HOME/.ssh/labsuser.pem}"
  echo "$SQL" | ssh -i "$LLAVE" "$SERVIDOR" \
    "cd hermandad-agenda && docker compose exec -T mysql sh -c '$MYSQL_CMD'" 2>&1 | grep -v "Using a password"
else
  cd "$(dirname "$0")/.."
  echo "$SQL" | docker compose exec -T mysql sh -c "$MYSQL_CMD" 2>&1 | grep -v "Using a password"
fi

if [ "$BORRAR" = 0 ]; then
  echo
  echo "No se borró nada. Para borrar, repite el comando agregando --borrar."
fi
echo "Nota: la reserva en línea permite 5 intentos fallidos cada 10 minutos por conexión; si ves «demasiados intentos», espera 10 minutos."
