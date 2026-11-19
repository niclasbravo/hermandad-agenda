#!/bin/bash
# Restaura un respaldo hecho con respaldo.sh. Uso: scripts/restaurar.sh respaldos/hermandad_agenda_AAAAMMDD_HHMMSS.sql.gz
# ADVERTENCIA: reemplaza la base actual por la del respaldo.
set -euo pipefail
cd "$(dirname "$0")/.."
ARCHIVO="${1:?Indica el archivo .sql.gz a restaurar}"
read -r -p "Esto reemplaza la base actual por $ARCHIVO. ¿Continuar? (escribe SI): " r
[ "$r" = "SI" ] || { echo "Cancelado."; exit 1; }
docker compose stop backend
gunzip -c "$ARCHIVO" | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
CERT="${ARCHIVO%.sql.gz}_certificados.tar.gz"
if [ -f "$CERT" ]; then
  docker compose start backend
  docker compose exec -T backend sh -c 'cd /datos && tar xzf -' < "$CERT"
else
  docker compose start backend
fi
echo "Restauración lista."
