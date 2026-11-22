# Agenda de atenciones · Hermandad de Dolores

Sistema de agendamiento y confirmación de atenciones para los 6 policlínicos del Instituto de Caridad Hermandad de Dolores.
Reemplaza el cuaderno, las fichas de papel y las planillas Excel por una agenda digital común con confirmación automática por WhatsApp.

Proyecto del curso Ingeniería de Software (UDD, 2026) · Equipo: Nicolás Bravo y José Tomás Amat.

- Especificación completa: [`docs/ESPECIFICACION.md`](docs/ESPECIFICACION.md)
- Decisiones de implementación: [`docs/DECISIONES_IMPLEMENTACION.md`](docs/DECISIONES_IMPLEMENTACION.md)
- Trazabilidad RF → caso de uso → clases → endpoint → pantalla → prueba: [`docs/TRAZABILIDAD.md`](docs/TRAZABILIDAD.md)
- Despliegue en la nube: [`docs/DESPLIEGUE_AWS.md`](docs/DESPLIEGUE_AWS.md) · Plantillas de WhatsApp: [`docs/WHATSAPP_PLANTILLAS.md`](docs/WHATSAPP_PLANTILLAS.md)

## Acceso para la evaluación

**Modalidad declarada: aplicación operativa en la nube** (AWS Academy Learner Lab, EC2 + Docker Compose + HTTPS).

| | |
|---|---|
| Sitio público (reserva en línea) | **https://100-59-97-56.sslip.io** |
| Ingreso del personal | **https://100-59-97-56.sslip.io/ingreso** |
| Usuario de prueba TENS/TONS | `c.munoz` (entra como TENS/TONS, Casa Central) |
| Usuario de prueba Administración | `l.martinez` (entra como Administración) |
| Contraseña (solo demo) | `Hermandad2026!` |
| Simulador de WhatsApp | https://100-59-97-56.sslip.io/simulador-whatsapp |

El Learner Lab apaga el servidor al terminar cada sesión de 4 horas. Para revisarlo fuera de una sesión activa, pídanle
al equipo que lo encienda (tarda ~3 minutos). Todos los datos son ficticios.

Para ejecutarlo localmente, ver [Cómo ejecutar](#cómo-ejecutar).

## Estado del proyecto

**Listo y verificado a mano contra la API:** backend completo (M1 a M8, seguridad por perfil, auditoría, reserva en línea, simulador y adapter de WhatsApp, webhook, programador de tareas), frontend con todas las pantallas del Figma, datos demo de 6 meses (ocupación de 91,5 % e inasistencia de 15,0 %), generador de carga (perfil `carga`), scripts de respaldo y restauración, Docker Compose y documentación. El recorrido «reservar → confirmación por WhatsApp → No podré asistir → cupo liberado con la lista de espera» funciona de punta a punta.

**Pruebas (verificado el 19-11-2026):** 93 pruebas unitarias del backend (dominio, servicios con repositorios en memoria, ArchUnit y plantillas de WhatsApp) y 12 pruebas de Vitest del frontend, todas en verde. `docker compose up --build` completo probado en local y en la nube.

**Falta:**
- [ ] **Pruebas de integración (Testcontainers):** están escritas en `backend/src/test/java/.../integracion/` (catálogo y agenda, citas, concurrencia CA-RF15-3, pacientes, lista de espera, confirmaciones y webhook, reserva en línea, asistencia y reportes, seguridad), pero no se terminaron de ejecutar ni de corregir.
- [ ] **Pruebas SQL:** faltan `db/03_datos_prueba_hermandad.sql` y `db/04_pruebas.sql` (las 10 operaciones inválidas de la sección 15.2 y el caso permitido), junto con la prueba Java que las ejecuta.
- [ ] **Pruebas del frontend:** `npm test` en verde (12 pruebas). Falta agregar la prueba «anular con aviso de lista de espera».
- [ ] **Revisión visual** de las pantallas contra `docs/pantallas/*.png`, incluido el ancho de celular (390 px).
- [x] **`docker compose up --build` completo** probado (local y nube).
- [x] **Despliegue en la nube** con HTTPS (AWS Academy Learner Lab).
- [ ] **Completar la columna «Prueba»** de `docs/TRAZABILIDAD.md`.
- [ ] **Logo real:** descargarlo del Figma a `frontend/public/logo.png` (hoy hay un marcador; DI-36).
- [ ] **Validar con la mandante:** las categorías de género y de relación del adulto responsable (DI-38) y la duración de alumnos según la planilla «Estándar de atención por profesional» (DI-41).
- [ ] **WhatsApp real:** las 6 plantillas están creadas en Meta y en revisión; falta su aprobación, el token permanente y las variantes para el adulto responsable (DI-25).

## Arquitectura

| Pieza | Tecnología |
|---|---|
| Cliente web | React 18 + TypeScript (Vite). Panel del personal y reserva en línea en una sola SPA adaptable al celular. |
| Backend | Java 21 + Spring Boot 3 (`agenda-hermandad.jar`). Monolito modular: `catalogo`, `agenda`, `pacientes`, `citas`, `listaespera`, `confirmaciones`, `asistencia`, `reportes`, `seguridad`, `compartido`. Cada módulo con `api → aplicacion → dominio` e `infraestructura`. |
| Base de datos | MySQL 8.0 con el esquema de `db/01_esquema_hermandad.sql` y los disparadores de `db/02_reglas_hermandad.sql`, sin cambios. Hibernate solo valida (`ddl-auto=validate`). |
| Mensajería | WhatsApp Cloud API detrás de `IServicioMensajeria`. En desarrollo y demo se usa un simulador. |
| Despliegue | Docker Compose: `mysql`, `backend` y `frontend` (Nginx sirve la SPA y hace de proxy de `/api` y `/webhooks`). En la nube se agrega Caddy (HTTPS) con `docker-compose.nube.yml`. |

## Cómo ejecutar

### Requisitos de ejecución

| Para | Necesitas |
|---|---|
| Correr el sistema completo | **Docker Desktop** (Windows/macOS) o Docker Engine (Linux) con **Compose v2.24 o superior**, 4 GB de RAM libres y el puerto 8080 libre |
| Desarrollar el backend sin Docker | Java 21 (Maven viene incluido como `./mvnw`) |
| Desarrollar el frontend | Node.js 20 o superior |
| Desplegar en la nube | Un servidor Ubuntu 22.04/24.04 de 2 GB con IP pública y Git Bash/SSH en tu PC (ver `docs/DESPLIEGUE_AWS.md`) |

Si ya tienes un MySQL local usando el puerto 3306, define `PUERTO_MYSQL=3307` en `.env`.

```bash
cp .env.example .env          # y cambia las contraseñas
docker compose up --build     # → http://localhost:8080 (portada) y http://localhost:8080/ingreso (personal)
```

La primera vez MySQL crea el esquema con `db/00`, `db/01` y `db/02` (vía única: `/docker-entrypoint-initdb.d`, sin Flyway).
Con `SPRING_PROFILES_ACTIVE=demo` (valor del `.env.example`) se cargan datos de demostración si la base está vacía.

### En la nube (AWS)

Guía completa en [`docs/DESPLIEGUE_AWS.md`](docs/DESPLIEGUE_AWS.md): el mismo Docker Compose más `docker-compose.nube.yml` (Caddy con HTTPS automático de Let's Encrypt). Hoy corre en una instancia EC2 t3.small del AWS Academy Learner Lab. Se actualiza con:

```bash
scripts/desplegar.sh ubuntu@100.59.97.56 ~/.ssh/labsuser.pem .env.nube
```

### Usuarios de demostración

Contraseña de todos: `Hermandad2026!` (solo para la demo).

| Usuario | Perfil | Policlínico base |
|---|---|---|
| `l.martinez` | Administración | — |
| `c.munoz` | TENS/TONS | Casa Central |
| `tens.santateresa`, `tens.sagradafamilia`, `tens.padrehurtado`, `tens.mariaayuda`, `tens.oratoriomariano` | TENS/TONS | su policlínico |

En el ingreso hay un solo botón, «Ingresar»: el perfil y el policlínico salen de la cuenta.

### Recorrido sugerido para la demo

1. Ingresa como `c.munoz` → **Agenda del día** de Casa Central (con las citas del prototipo).
2. **Reservar cita** → RUT `15.432.109-8` → Medicina → elige una hora → «Confirmar reserva».
3. Abre **http://localhost:8080/simulador-whatsapp**: llega la confirmación. Usa «Forzar envío del recordatorio ahora» y responde «No podré asistir».
4. Vuelve a la agenda del día de esa fecha: la cita quedó anulada y aparece en amarillo el cupo liberado con la primera persona de la lista de espera.
5. Reserva en línea: en la portada, «Reservar hora» con un RUT nuevo → completa los datos → elige hora → sube certificados. La TENS/TONS la verifica desde la agenda del día («Verificar reserva en línea»).

### Perfiles de Spring

| Perfil | Uso |
|---|---|
| `dev` (por defecto) | Desarrollo local; simulador de WhatsApp disponible en `/simulador-whatsapp`. |
| `demo` | Igual que `dev` y carga los datos de demostración (6 meses de historia, fechas relativas a hoy). |
| `carga` | Generador de datos de carga: 12 meses de historia en los 6 policlínicos (RNF-07). Úsalo con una base vacía. |
| `prod` | Sin simulador de páginas ni datos demo. Define `ADMIN_INICIAL_USUARIO` y `ADMIN_INICIAL_CONTRASENA` para crear la primera cuenta de Administración. |

## Desarrollo local sin Docker para el backend

```bash
docker compose up -d mysql
cd backend && DB_CONTRASENA=<la de .env> SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
cd frontend && npm install && npm run dev     # http://localhost:5173 (proxy de /api al 8080)
```

## Flujo de trabajo con Git

- `main` es la versión estable y la que se despliega. No se trabaja directo sobre `main`.
- Cada cambio va en su rama: `feature/<tema>` para funcionalidades (`feature/whatsapp-real`), `fix/<tema>` para correcciones y `docs/<tema>` para documentación.
- La rama se integra a `main` con un **pull request** en GitHub. El otro integrante lo revisa antes de fusionarlo.
- Mensajes de commit en español, en imperativo y explicando el porqué (por ejemplo: «WhatsApp: enviar solo las variables de cada plantilla»).
- Nunca se suben `.env`, `.env.nube`, llaves `.pem` ni datos reales de pacientes.

```bash
git switch main && git pull
git switch -c feature/mi-cambio
# ...cambios y pruebas...
git add -A && git commit -m "Describe el cambio"
git push -u origin feature/mi-cambio   # luego abre el pull request en GitHub
```

## Pruebas

```bash
cd backend && ./mvnw test        # unitarias, ArchUnit e integración con Testcontainers (MySQL 8.0)
cd frontend && npm test          # componentes y flujos (Vitest + Testing Library + MSW)
cd frontend && npm run e2e       # recorridos A y H con Playwright (requiere backend demo y `npm run dev`)
```

Pruebas SQL: `db/03_datos_prueba_hermandad.sql` (datos fijos) y `db/04_pruebas.sql` (las 10 operaciones que el modelo debe rechazar y el caso permitido).

Con Colima en macOS, Testcontainers necesita:
```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Activar WhatsApp real (WhatsApp Cloud API de Meta)

1. En la cuenta de desarrollador de Meta crea las plantillas `hermandad_confirmacion`, `hermandad_confirmacion_pendiente`, `hermandad_recordatorio`, `hermandad_refuerzo`, `hermandad_anulacion` y `hermandad_anulacion_policlinico` (textos en la tabla `plantilla_mensaje`). El recordatorio y el refuerzo llevan dos botones de respuesta rápida con las cargas `ASISTIRA` y `NO_ASISTIRA`. Los parámetros del cuerpo se envían en este orden: nombre de pila, Fecha, hora, profesión, profesional, policlínico, box, dirección, fecha.
2. En `.env`: `MENSAJERIA_PROVEEDOR=whatsapp`, `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` y `WHATSAPP_API_VERSION`.
3. Configura el webhook en Meta: `https://<tu-dominio>/webhooks/whatsapp` con el mismo `WHATSAPP_VERIFY_TOKEN`, suscrito a `messages`. La firma `X-Hub-Signature-256` se verifica con el App Secret.
4. `docker compose up -d --build backend`.

Nunca subas credenciales al repositorio: `.env` está en `.gitignore`.

## Respaldos (RNF-10, RNF-11)

```bash
scripts/respaldo.sh                     # mysqldump comprimido + certificados → ./respaldos/
scripts/restaurar.sh respaldos/hermandad_agenda_AAAAMMDD_HHMMSS.sql.gz
```

Programa el respaldo diario con cron (ej.: `0 2 * * * /ruta/hermandad-agenda/scripts/respaldo.sh`). Se conservan 30 días (`DIAS_A_CONSERVAR`).
Los contenedores usan `restart: unless-stopped`. En producción, pon HTTPS delante de Nginx.
