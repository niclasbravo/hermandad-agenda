#!/bin/bash
# Se ejecuta antes de 01 y 02 (orden alfabético de /docker-entrypoint-initdb.d).
# El usuario de la aplicación solo tiene permisos sobre hermandad_agenda.
set -e
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${DB_USUARIO:-agenda}'@'%' IDENTIFIED BY '${DB_CONTRASENA:-agenda}';
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON hermandad_agenda.* TO '${DB_USUARIO:-agenda}'@'%';
FLUSH PRIVILEGES;
SQL
