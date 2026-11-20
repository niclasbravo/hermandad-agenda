#!/usr/bin/env bash
# Sube la versión actual del repositorio al servidor y la levanta con HTTPS.
# Se ejecuta en TU computador (Git Bash en Windows), desde la raíz del proyecto:
#   scripts/desplegar.sh ubuntu@IP_DEL_SERVIDOR ruta/a/llave.pem [archivo.env]
#
# - Envía el código de HEAD (git archive), no la carpeta completa: sin node_modules, target ni .env locales.
# - Envía el archivo de variables como ~/hermandad-agenda/.env (por defecto .env.nube, si no existe .env).
# - Reconstruye y reinicia los contenedores. Los datos de MySQL y los certificados quedan en volúmenes.
set -euo pipefail

SERVIDOR="${1:?Uso: scripts/desplegar.sh ubuntu@IP llave.pem [archivo.env]}"
LLAVE="${2:?Falta la ruta a la llave .pem}"
ENV_LOCAL="${3:-}"
if [ -z "$ENV_LOCAL" ]; then
  if [ -f .env.nube ]; then ENV_LOCAL=.env.nube; else ENV_LOCAL=.env; fi
fi
[ -f "$ENV_LOCAL" ] || { echo "No existe $ENV_LOCAL"; exit 1; }
grep -q '^DOMINIO=.\+' "$ENV_LOCAL" || { echo "Falta DOMINIO=... en $ENV_LOCAL"; exit 1; }

SSH=(ssh -i "$LLAVE" -o StrictHostKeyChecking=accept-new "$SERVIDOR")
DESTINO=hermandad-agenda

echo "== Enviando código ($(git rev-parse --short HEAD)) =="
git archive --format=tar HEAD | "${SSH[@]}" "mkdir -p $DESTINO && tar -x -C $DESTINO"

echo "== Enviando variables ($ENV_LOCAL) =="
scp -i "$LLAVE" -q "$ENV_LOCAL" "$SERVIDOR:$DESTINO/.env"
"${SSH[@]}" "chmod 600 $DESTINO/.env"

echo "== Construyendo y levantando (la primera vez tarda varios minutos) =="
"${SSH[@]}" "cd $DESTINO && docker compose -f docker-compose.yml -f docker-compose.nube.yml up -d --build --remove-orphans && docker image prune -f >/dev/null && docker compose -f docker-compose.yml -f docker-compose.nube.yml ps"

DOMINIO=$(grep '^DOMINIO=' "$ENV_LOCAL" | cut -d= -f2- | tr -d '\r')
echo
echo "Listo: https://$DOMINIO  (el certificado HTTPS puede tardar 1 minuto la primera vez)"
