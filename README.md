# Agenda de atenciones · Hermandad de Dolores

Sistema de agendamiento y confirmación de atenciones para los 6 policlínicos del Instituto de Caridad Hermandad de Dolores.
Reemplaza el cuaderno, las fichas de papel y las planillas Excel por una agenda digital común con confirmación automática por WhatsApp.

Proyecto del curso Ingeniería de Software (UDD, 2026) · Equipo: Nicolás Bravo y José Tomás Amat.

- Especificación completa: [`docs/ESPECIFICACION.md`](docs/ESPECIFICACION.md)
- Decisiones de implementación: [`docs/DECISIONES_IMPLEMENTACION.md`](docs/DECISIONES_IMPLEMENTACION.md)
- Trazabilidad RF → caso de uso → clases → endpoint → pantalla → prueba: [`docs/TRAZABILIDAD.md`](docs/TRAZABILIDAD.md)

## Estado del proyecto

**Listo y verificado a mano contra la API:** backend completo (M1 a M8, seguridad por perfil, auditoría, reserva en línea, simulador y adapter de WhatsApp, webhook, programador de tareas), frontend con todas las pantallas del Figma, datos demo de 6 meses (ocupación de 91,5 % e inasistencia de 15,0 %), generador de carga (perfil `carga`), scripts de respaldo y restauración, Docker Compose y documentación. El recorrido «reservar → confirmación por WhatsApp → No podré asistir → cupo liberado con la lista de espera» funciona de punta a punta.

**Pruebas:** las 91 pruebas unitarias del backend (dominio, servicios con repositorios en memoria y ArchUnit) están en verde.

**Falta:**
- [ ] **Pruebas de integración (Testcontainers):** están escritas en `backend/src/test/java/.../integracion/` (catálogo y agenda, citas, concurrencia CA-RF15-3, pacientes, lista de espera, confirmaciones y webhook, reserva en línea, asistencia y reportes, seguridad), pero no se terminaron de ejecutar ni de corregir.
- [ ] **Pruebas SQL:** faltan `db/03_datos_prueba_hermandad.sql` y `db/04_pruebas.sql` (las 10 operaciones inválidas de la sección 15.2 y el caso permitido), junto con la prueba Java que las ejecuta.
- [ ] **Pruebas del frontend:** los e2e de Playwright en `frontend/e2e/` (recorridos A y H, incluido el ancho de celular) pasan contra el backend demo. Las 5 pruebas de Vitest de `frontend/src/pruebas/` están escritas, pero falta confirmar `npm test` en verde y agregar la prueba «anular con aviso de lista de espera».
- [ ] **Revisión visual** de las pantallas contra `docs/pantallas/*.png`, incluido el ancho de celular (390 px).
- [ ] **Probar `docker compose up --build` completo** (se probaron MySQL en Docker y el backend y el frontend en local, pero no las imágenes de backend y frontend).
- [ ] **Completar la columna «Prueba»** de `docs/TRAZABILIDAD.md`.
- [ ] **Logo real:** descargarlo del Figma a `frontend/public/logo.png` (hoy hay un marcador; DI-36).
- [ ] **Validar con la mandante:** las categorías de género y de relación del adulto responsable (DI-38) y la duración de alumnos según la planilla «Estándar de atención por profesional» (DI-41).
- [ ] **WhatsApp real:** aprobar las plantillas en Meta, incluidas las variantes para el adulto responsable (DI-25).

## Arquitectura

| Pieza | Tecnología |
|---|---|
| Cliente web | React 18 + TypeScript (Vite). Panel del personal y reserva en línea en una sola SPA adaptable al celular. |
| Backend | Java 21 + Spring Boot 3 (`agenda-hermandad.jar`). Monolito modular: `catalogo`, `agenda`, `pacientes`, `citas`, `listaespera`, `confirmaciones`, `asistencia`, `reportes`, `seguridad`, `compartido`. Cada módulo con `api → aplicacion → dominio` e `infraestructura`. |
| Base de datos | MySQL 8.0 con el esquema de `db/01_esquema_hermandad.sql` y los disparadores de `db/02_reglas_hermandad.sql`, sin cambios. Hibernate solo valida (`ddl-auto=validate`). |
| Mensajería | WhatsApp Cloud API detrás de `IServicioMensajeria`. En desarrollo y demo se usa un simulador. |
| Despliegue | Docker Compose: `mysql`, `backend` y `frontend` (Nginx sirve la SPA y hace de proxy de `/api` y `/webhooks`). |

## Cómo ejecutar

Requisitos: Docker con Compose.

```bash
cp .env.example .env          # y cambia las contraseñas
docker compose up --build     # → http://localhost:8080 (portada) y http://localhost:8080/ingreso (personal)
```

La primera vez MySQL crea el esquema con `db/00`, `db/01` y `db/02` (vía única: `/docker-entrypoint-initdb.d`, sin Flyway).
Con `SPRING_PROFILES_ACTIVE=demo` (valor del `.env.example`) se cargan datos de demostración si la base está vacía.

### En la nube (AWS)

Guía completa en [`docs/DESPLIEGUE_AWS.md`](docs/DESPLIEGUE_AWS.md): mismo Docker Compose más `docker-compose.nube.yml` (Caddy con HTTPS automático), en un servidor Lightsail de 2 GB. Se despliega con `scripts/desplegar.sh ubuntu@IP llave.pem`.

### Usuarios de demostración

Contraseña de todos: `Hermandad2026!` (solo para la demo).

| Usuario | Perfil | Policlínico base |
|---|---|---|
| `l.martinez` | Administración | — |
| `c.munoz` | TENS/TONS | Casa Central |
| `tens.santateresa`, `tens.sagradafamilia`, `tens.padrehurtado`, `tens.mariaayuda`, `tens.oratoriomariano` | TENS/TONS | su policlínico |

En el ingreso, elige el botón del perfil que corresponde al usuario («Ingresar como TENS/TONS» o «Ingresar como Administración»).

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
