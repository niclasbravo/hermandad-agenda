#!/bin/bash
# Respaldo diario de la base (RNF-11). Uso: scripts/respaldo.sh [directorio]
# Programarlo con cron, por ejemplo a las 02:00:  0 2 * * * /ruta/hermandad-agenda/scripts/respaldo.sh
set -euo pipefail
cd "$(dirname "$0")/.."
DESTINO="${1:-./respaldos}"
DIAS_A_CONSERVAR="${DIAS_A_CONSERVAR:-30}"
mkdir -p "$DESTINO"
ARCHIVO="$DESTINO/hermandad_agenda_$(date +%Y%m%d_%H%M%S).sql.gz"
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --set-gtid-purged=OFF --databases hermandad_agenda' | gzip > "$ARCHIVO"
# Certificados (RF-41): están fuera de la base, en el volumen del backend.
docker compose exec -T backend sh -c 'cd /datos && tar czf - certificados' > "${ARCHIVO%.sql.gz}_certificados.tar.gz" || echo "Aviso: no se pudieron respaldar los certificados"
find "$DESTINO" -name 'hermandad_agenda_*' -mtime +"$DIAS_A_CONSERVAR" -delete
echo "Respaldo listo: $ARCHIVO"
