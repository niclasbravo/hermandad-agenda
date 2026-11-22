# Sistema de Agendamiento y Confirmación de Atenciones · Hermandad de Dolores

> **Para Claude Code.** Este documento es la especificación completa para construir el sistema. Reúne todo lo definido en el proyecto del curso Ingeniería de Software (UDD, 2026). Las fuentes son el levantamiento, la especificación de requisitos, la viabilidad y factibilidad, el análisis funcional (CU-01 a CU-08), la planificación, el diseño de datos, el diseño de clases y patrones, la arquitectura 4+1 y el prototipo en Figma.
> **No inventes funciones ni reglas.** Cuando algo no esté aquí, aplica la regla más conservadora, deja una nota en `docs/DECISIONES_IMPLEMENTACION.md` y sigue avanzando.
> Todo lo que vea el usuario (interfaz, mensajes y errores) va en **español de Chile**, en lenguaje simple y sin abreviaturas ni tecnicismos (RNF-02).

Equipo: Nicolás Bravo y José Tomás Amat · Profesora: Elizabeth Chicata Castro · Mandante: Laura Gaju Nicolau (Administradora General), contraparte Ruby Anne Armaza.

---

## 0. Contenido de este kit y cómo usarlo

```
README.md                          ← este documento (la especificación)
db/01_esquema_hermandad.sql        ← modelo físico MySQL 8.0, 26 tablas. Copiado tal cual del informe de Diseño de Datos. NO cambiar la estructura.
db/02_reglas_hermandad.sql         ← 5 disparadores de integridad. Copiado tal cual del informe.
docs/PANTALLAS_FIGMA.md            ← todos los textos de las ~100 pantallas del prototipo, en orden de lectura, con su id de nodo de Figma
docs/pantallas/*.png               ← capturas de las pantallas clave (referencia visual obligatoria)
```

**Prototipo en Figma** (fuente de la verdad visual): archivo `M8rRUwWfGDPKmlZvBwNcWA`. Usa la página **«Prototipo v3 (visual)»** (id `222:1620`), que es la versión final.
- Diseño: https://www.figma.com/design/M8rRUwWfGDPKmlZvBwNcWA
- Si tienes el MCP de Figma disponible, usa `get_screenshot` o `get_design_context` con los ids de nodo que aparecen en `docs/PANTALLAS_FIGMA.md` para ver cada pantalla con detalle. Si no lo tienes, usa las capturas de `docs/pantallas/` y los textos de `docs/PANTALLAS_FIGMA.md`.
- En el prototipo hay recuadros amarillos punteados con el texto **«Atajos del prototipo (no son parte del sistema)»**. Esos botones **no se implementan**.
- Las etiquetas verdes del tipo «Validado 13/10: …» documentan decisiones del diseño. No van en la interfaz real; puedes omitirlas.

**Forma de trabajo esperada (cascada, por paquetes de la EDT):**
1. Lee este README completo. Mira todas las imágenes de `docs/pantallas/` y recorre `docs/PANTALLAS_FIGMA.md`.
2. Arma el repositorio (sección 3) y levanta MySQL con los dos scripts. Verifica que ejecuten sin errores.
3. Construye por fases, en este orden (sección 17). Cada fase termina con sus pruebas en verde y una prueba manual en el navegador.
4. Mantén actualizados `docs/DECISIONES_IMPLEMENTACION.md` y `docs/TRAZABILIDAD.md`. La trazabilidad va de RF a endpoint, a clase, a pantalla y a prueba.
5. Termina con la lista de verificación de la sección 19.

---

## 1. Contexto

El Instituto de Caridad Hermandad de Dolores es una institución sin fines de lucro con **6 policlínicos** gratuitos en la Región Metropolitana, que atienden más de **30.000 atenciones al año** (unas **2.500 al mes**). Hoy las TENS/TONS agendan en cuaderno o en papel, confirman por WhatsApp desde el celular del policlínico y traspasan la asistencia a planillas Excel en OneDrive. La ficha clínica es de papel.

**Objetivo:** reemplazar el cuaderno, las fichas de papel y las planillas Excel por una **agenda digital común** con **confirmación automática por WhatsApp**. Esa es la «base fundacional» que pidió la mandante. Se agregan lista de espera, registro de asistencia, reportes y una **reserva en línea** para el paciente o un familiar.

**Problemas del proceso actual (diagnóstico D1 a D8):** D1, consulta manual de la disponibilidad. D2, sobreagendamiento por el desajuste entre los bloques y la duración real de la atención. D3, confirmación manual. D4, doble registro (papel y Excel). D5, sin registro digital ni historial. D6, sin lista de espera ni seguimiento de inasistencias. D7, la verificación de FONASA/CESFAM no queda registrada. D8, reportería manual y poco periódica.

**El sistema NO guarda información clínica** (RES-08). Solo agenda y confirmación.

### 1.1 Actores
| Actor | Qué hace |
|---|---|
| **Administración** (perfil) | Mantiene policlínicos, box, profesiones y su duración, y profesionales. Define los bloques horarios, bloquea cupos, configura la publicación, el % de cupos web, el tope y los refuerzos, y ve todos los reportes. |
| **TENS/TONS** (perfil, operadora principal) | Busca y registra pacientes, registra la verificación FONASA/CESFAM, reserva, anula y agenda series, maneja la lista de espera, registra la asistencia, gestiona confirmaciones (llamadas y reenvíos) y ve el listado de inasistencia reiterada de su policlínico. |
| **Paciente / Adulto responsable** | Recibe la confirmación, el recordatorio y los refuerzos por WhatsApp y puede responder «No podré asistir». Puede **reservar en línea** sin cuenta, identificándose con su documento y su fecha de nacimiento. |
| **Servicio de mensajería** (sistema externo) | WhatsApp Cloud API (Meta). Entrega los mensajes y avisa por webhook la entrega y las respuestas. |

No son actores: el profesional de salud (no usa el sistema), el Directorio, ni FONASA o CESFAM (la verificación es visual, RES-03).

### 1.2 Policlínicos (datos reales del mandante)
| Policlínico | Dirección | Comuna |
|---|---|---|
| Casa Central | San Isidro 218 | Santiago Centro |
| Santa Teresa | Gregorio Olmos 4210, esquina Manuel Chacón | Estación Central |
| Sagrada Familia | Almirante Latorre 9921, Población La Bandera | San Ramón |
| Padre Hurtado | Pasaje 7, esquina Pasaje 10, El Cortijo | Conchalí |
| María Ayuda | Profesor Alcaino 1393 | Puente Alto |
| Oratorio Mariano | Av. Libertador Bernardo O'Higgins 909 | Maipú |

Profesiones del equipo: médico general, kinesiólogo, fonoaudiólogo, psicólogo, odontólogo, nutricionista, matrón y podólogo.

---

## 2. Stack obligatorio (arquitectura 4+1 aprobada)

| Elemento | Tecnología |
|---|---|
| Cliente web | **React + TypeScript** (SPA con Vite). Una sola base de código para el **panel del personal** y la **reserva en línea**, adaptable al celular. |
| Backend | **Java 21 + Spring Boot 3** (Web, Security, Data JPA, Validation, Scheduling). Un solo artefacto `agenda-hermandad.jar`. |
| Base de datos | **MySQL 8.0** (InnoDB, utf8mb4, `utf8mb4_spanish_ci`). Esquema = `db/01` + `db/02` **sin cambios estructurales**. |
| Mensajería | **WhatsApp Cloud API (Meta)** detrás de la interfaz `IServicioMensajeria`. En desarrollo y en la demo se usa un **simulador** (sección 11.6). |
| Archivos | Certificados de RF-41 **fuera de la base**, detrás de `IAlmacenArchivos`. Implementación local en disco (volumen Docker). Una implementación S3 queda como opcional. |
| Despliegue | **Docker Compose**: `mysql` + `backend` (JVM 21) + `frontend` (Nginx, que sirve React y hace proxy de `/api/**` y `/webhooks/**` al backend). |

**Estilo arquitectónico:** monolito modular en capas, consumido como cliente-servidor y organizado en MVC. React es la Vista, los controladores REST son el Controlador, y la aplicación y el dominio son el Modelo. Se usan los patrones **Repository** (en todos los módulos, con el detalle completo en Citas) y **Adapter** (mensajería). **No se usa Strategy**: los parámetros cambian valores, no algoritmos. **No hay microservicios ni broker de eventos.** Lo asíncrono es un programador de tareas (`@Scheduled`, cada minuto) más un webhook.

---

## 3. Estructura del repositorio

```
hermandad-agenda/
├─ docker-compose.yml
├─ .env.example
├─ README.md                      (instrucciones para ejecutar; puedes partir de este)
├─ db/
│  ├─ 01_esquema_hermandad.sql
│  ├─ 02_reglas_hermandad.sql
│  ├─ 03_datos_prueba_hermandad.sql   (lo escribes tú: datos ficticios fijos para las pruebas SQL)
│  └─ 04_pruebas.sql                  (lo escribes tú: las 10 operaciones inválidas de la sección 15.2)
├─ backend/   (Maven, groupId cl.udd.hermandad, artifactId agenda-hermandad)
│  └─ src/main/java/cl/udd/hermandad/agenda/
│     ├─ catalogo/        (M1)  api · aplicacion · dominio · infraestructura
│     ├─ agenda/          (M2)  api · aplicacion · dominio · infraestructura
│     ├─ pacientes/       (M3)  api · aplicacion · dominio · infraestructura
│     ├─ citas/           (M4)  api · aplicacion · dominio · infraestructura
│     ├─ listaespera/     (M5)  api · aplicacion · dominio · infraestructura
│     ├─ confirmaciones/  (M6)  api · aplicacion · dominio · infraestructura
│     ├─ asistencia/      (M7)  api · aplicacion · dominio · infraestructura
│     ├─ reportes/        (M8)  api · aplicacion · dominio · infraestructura
│     ├─ seguridad/       (transversal: usuarios, perfiles, parámetros, auditoría)
│     └─ compartido/      (configuración, seguridad web, manejo de errores, programador de tareas, reloj)
├─ frontend/
│  └─ src/
│     ├─ panel/              (panel del personal: TENS/TONS y Administración)
│     ├─ reserva-en-linea/   (portada pública y reserva del paciente)
│     └─ compartido/         (diseño, componentes, cliente de la API, utilidades de RUT y fechas)
└─ docs/
   ├─ DECISIONES_IMPLEMENTACION.md
   ├─ TRAZABILIDAD.md
   ├─ PANTALLAS_FIGMA.md
   └─ pantallas/*.png
```

**Reglas de dependencia (obligatorias):**
- Dentro de cada módulo: `api → aplicacion → dominio`. La `infraestructura` implementa las interfaces del dominio, **nunca al revés**.
- Un módulo usa a otro **solo por su servicio o su interfaz pública**. Por ejemplo, `citas` no accede a las tablas de `pacientes`: llama a `PacienteService`.
- El dominio no conoce Spring MVC, HTTP ni el proveedor de WhatsApp.
- Agrega una prueba de arquitectura con **ArchUnit** que verifique estas reglas.

---

## 4. Glosario y estados

- **Cupo:** horario reservable que genera un bloque. **Su estado NO se guarda; se deriva** (`EstadoCupo`):
  - `BLOQUEADO`: `cupo.id_bloqueo` no es nulo.
  - `RESERVADO`: tiene una cita vigente (`cita.id_cupo_activo = id_cupo`, es decir, una cita no anulada).
  - `NO_PUBLICADO`: `cupo.fecha > hoy + profesional.plazo_publicacion_dias`.
  - `DISPONIBLE`: ninguno de los anteriores, y la fecha y hora son futuras.
  - Prioridad de evaluación: BLOQUEADO > RESERVADO > NO_PUBLICADO > DISPONIBLE.
- **Cita** (`EstadoCita`): `PENDIENTE_VERIFICACION`, `RESERVADA`, `ANULADA`, `ASISTIDA`, `INASISTIDA`. **Nunca se borra** (D-17).
- **Confirmación** (`EstadoConfirmacion`): `SIN_ENVIAR`, `ENVIADA`, `NO_ENVIADA`, `CONFIRMADA`, `CONFIRMADA_TELEFONO`, `SIN_RESPUESTA`.
- **Paciente habilitado:** su verificación de requisitos **más reciente** tiene resultado `HABILITADO` (RN-01).
- **Profesional.duracionAtencion():** si `es_alumno_practica` y la profesión tiene `duracion_alumno_min`, se usa esa duración; si no, se usa `duracion_min`. Si el valor resultante es NULL, **no se generan cupos**.
- **Por llamar** (`Cita.requiereLlamada()`): la cita está vigente para hoy o mañana y su estado de confirmación es `NO_ENVIADA` o `SIN_RESPUESTA`.

### 4.1 Transiciones de la cita (aplicarlas en `Cita`, no en el servicio)
```
(nueva, TENS, paciente habilitado)            → RESERVADA
(nueva, EN_LINEA, paciente habilitado)        → RESERVADA
(nueva, EN_LINEA, paciente no habilitado)     → PENDIENTE_VERIFICACION   (RN-13; también lo hace el disparador)
PENDIENTE_VERIFICACION --verificación HABILITADO--> RESERVADA
RESERVADA | PENDIENTE_VERIFICACION --anular(origen, motivo)--> ANULADA   (exige fecha_anulacion y origen)
RESERVADA --registrarAsistencia(true|false)--> ASISTIDA | INASISTIDA   (solo una vez, solo el día de la cita o después)
ANULADA, ASISTIDA, INASISTIDA: estados finales (no admiten más cambios)
```
Una cita `PENDIENTE_VERIFICACION` no admite registro de asistencia hasta que se verifique: la TENS/TONS verifica cuando llega el paciente. Si no presenta los certificados, la anula (D-03).

### 4.2 Transiciones de la confirmación
```
al reservar                                      → SIN_ENVIAR  (+ mensaje CONFIRMACION en estado PROGRAMADO, en la misma transacción)
confirmación ENVIADO / ENTREGADO / LEIDO         → ENVIADA
confirmación FALLIDO (tras los reintentos)       → NO_ENVIADA  (queda «por llamar», D-15)
recordatorio enviado y sin respuesta             → SIN_RESPUESTA
respuesta «Asistiré»                             → CONFIRMADA
respuesta «No podré asistir»                     → la cita se ANULA (origen PACIENTE_MENSAJE) y el cupo se libera
llamada CONFIRMO                                 → CONFIRMADA_TELEFONO
llamada NO_ASISTIRA                              → la cita se ANULA (origen TENS)
llamada NO_CONTESTO                              → no cambia
```

---

## 5. Reglas de negocio (RN), restricciones (RES) y decisiones (D)

### 5.1 Reglas de negocio
| ID | Regla | Dónde se aplica |
|---|---|---|
| RN-01 | Solo se agenda y atiende a un paciente con afiliación a FONASA e inscripción en su CESFAM verificadas. Se exige en la primera atención. Vale la verificación más reciente. | `Paciente.estaHabilitado()`; `AgendamientoService.reservar()` responde 422; disparador `trg_cita_reglas_ins` |
| RN-02 | Un cupo solo se reserva si está publicado, no está bloqueado y no tiene otra reserva vigente. | `Cupo.estaDisponible()`; `SELECT … FOR UPDATE`; clave única `uq_cita_cupo_activo`; disparador |
| RN-03 | Los bloques horarios los define la Administración. | Permisos (sección 6) |
| RN-04 | La duración del cupo es la de la profesión: médico 20 min; kinesiólogo, odontólogo y matrón 30 min; psicólogo 45 min. Fonoaudiólogo, nutricionista y podólogo están **por definir (NULL)**. | `Profesion`, `BloqueHorario.generarCupos()` |
| RN-05 | Un paciente menor de edad requiere un adulto responsable (nombre, documento, teléfono y relación). | Validación en el servicio y en el formulario; disparador `trg_paciente_menor_*` |
| RN-06 | No hay priorización de pacientes: se atiende por orden de solicitud y de llegada (lista de espera por `fecha_orden`). | `ListaEsperaService` |
| RN-07 | Una hora anulada vuelve a quedar disponible. | `id_cupo_activo` pasa a NULL al anular |
| RN-08 | Los cupos se publican de forma gradual: se muestran solo hasta `plazo_publicacion_dias` por profesional (valor validado: 7 días, ajustable por profesional). | `EstadoCupo.NO_PUBLICADO` |
| RN-09 | El correo del paciente no es obligatorio. | DTOs y formularios |
| RN-10 | Hay inasistencia reiterada cuando el paciente alcanza el umbral (`UMBRAL_INASISTENCIA`, inicial 3) en el período. | `ListadoInasistencia` |
| RN-11 | En una serie de sesiones, una sesión inasistida **no se repone automáticamente**. La cantidad la indica el profesional. | `SerieSesiones` |
| RN-12 (D-09) | Un profesional y un box no pueden tener bloques superpuestos (mismo día de la semana, horas que se cruzan y vigencias que se cruzan). | `BloqueHorario.seSuperponeCon()` + disparador |
| RN-13 | Una cita en línea de un paciente sin verificación queda `PENDIENTE_VERIFICACION` hasta que la TENS/TONS registre los certificados en la primera atención. Sin inscripción en CESFAM no se atiende. | `AgendamientoService.reservarEnLinea()` + disparador |
| RN-14 (D-02) | Un paciente no puede tener más reservas **en línea** activas que el tope (`TOPE_RESERVAS_WEB`). NULL significa sin tope (valor por defecto). | `ReglaReservaWeb.superaTope()` |

### 5.2 Restricciones
- **RES-01:** la TENS/TONS sigue siendo la operadora principal. La interfaz se orienta a ella y la reserva en línea es la segunda vía.
- **RES-02:** las solicitudes siguen llegando por WhatsApp, llamada y presencialmente. El sistema no reemplaza esos canales, solo el cuaderno.
- **RES-03:** la verificación de FONASA/CESFAM es visual y no hay integración. Se registra manualmente.
- **RES-04:** hay muchos adultos mayores, varias nacionalidades y horas que toma un familiar. Por eso: lenguaje simple, tipografía legible y adulto responsable.
- **RES-05:** institución sin fines de lucro. Solo software libre, sin licencias pagadas.
- **RES-06:** Ley 21.719 (vigente desde el 01/12/2026) y Ley 20.584. Exigen control de acceso, auditoría y minimización de datos. La reserva pública **nunca muestra datos de la ficha**.
- **RES-07:** la interoperabilidad es futura. No se construye, pero tampoco se impide (repositorios e interfaces).
- **RES-08:** sin información clínica.

### 5.3 Decisiones del equipo (línea base v1.0, requisitos congelados)
| ID | Decisión |
|---|---|
| D-01 | El % de cupos web es un parámetro (`PORCENTAJE_CUPOS_WEB`). Parte sin configurar (NULL = 0 % en la web) y lo fija la Administración. |
| D-02 | El tope de reservas en línea es opcional y parte **desactivado** (`TOPE_RESERVAS_WEB` = NULL). |
| D-03 | Si un paciente nuevo no presenta los certificados, no se atiende: la TENS/TONS anula la cita y registra la verificación como `NO_CUMPLE`. |
| D-04 | Los refuerzos del recordatorio son configurables: `REFUERZOS_CANTIDAD` y `REFUERZOS_HORAS_ANTES`. Parten en NULL (sin refuerzos). |
| D-05 | Para retirar a alguien de la lista de espera hay que indicar un motivo: `HORA_ASIGNADA`, `DESISTE` u `OTRO`. Si se le asigna una hora, la inscripción queda ligada a esa cita. |
| D-06 | Si en una serie no hay cupo en algunas fechas, el sistema muestra esas fechas y la TENS/TONS decide: reservar solo las disponibles o cambiar el día o la hora. |
| D-07 | Si el paciente tiene adulto responsable, **los mensajes van a ambos**: un registro `mensaje` por destinatario. |
| D-08 | Si el profesional es alumno en práctica, se usa la duración de alumnos de su profesión. |
| D-09 | No se permiten bloques superpuestos de un mismo profesional ni de un mismo box. |
| D-10 | Al modificar o desactivar un bloque que tiene reservas, el sistema muestra las reservas afectadas y obliga a reasignarlas o anularlas con aviso al paciente (plantilla ANULACION). Nunca se borran en silencio. |
| D-11 | Reagendar = anular la cita y reservar una nueva (en una sola operación de la interfaz). |
| D-12 | Solo la Administración bloquea cupos, y siempre con motivo. |
| D-13 | El umbral de inasistencia es configurable (inicial 3) y los tramos de edad también. **Demanda = reservas + inscripciones en lista de espera.** |
| D-14 | Los pacientes se identifican con tipo y número de documento (RUT, PASAPORTE u OTRO). El dígito verificador se valida **solo** si el tipo es RUT. |
| D-15 | Si el WhatsApp no llega o el paciente no responde, la TENS/TONS llama y registra el resultado. **No se usa SMS.** |
| D-16 | La agenda se ve **en lista, por profesional o por box**; elige el usuario. |
| D-17 | Citas, inscripciones, mensajes, bloques y profesionales **no se eliminan**: cambian de estado o se desactivan, y todo queda auditado. **No hay endpoints DELETE para estos recursos.** |
| A-01 | (22/10) Los mensajes por WhatsApp son **obligatorios**. No se pregunta si el paciente autoriza el contacto: se le informa al registrarlo y se guarda `paciente.fecha_aviso_mensajes`. |

---

## 6. Perfiles y permisos (RNF-04)

Autenticación con usuario y contraseña (BCrypt). Sesión de servidor con cookie `HttpOnly` y `SameSite=Lax`, más protección CSRF (`CookieCsrfTokenRepository`, cabecera `X-XSRF-TOKEN`). Sin sesión, la API interna responde 401 y la interfaz redirige al ingreso: **sin sesión no se muestra ninguna información** (CA-RNF04-2).

| Función | Administración | TENS/TONS |
|---|---|---|
| Policlínicos, box, profesiones, duraciones, profesionales y credenciales (CU-01) | ✅ | ❌ |
| Bloques horarios, generación de cupos y modificación con reservas afectadas (CU-02) | ✅ | ❌ (CA-RNF04-1: si lo intenta, 403) |
| Bloquear cupos (D-12) | ✅ | ❌ |
| Publicación, % web, tope, refuerzos y umbral (parámetros) | ✅ | ❌ |
| Ver la agenda de cupos semanal | ✅ | ✅ (solo lectura, vía agenda del día por profesional o por box) |
| Pacientes: buscar, registrar, editar, verificar e historial (CU-03) | ✅ (lectura) | ✅ |
| Reservar, anular, reagendar, series y lista de espera (CU-04) | ❌ | ✅ |
| Agenda del día, asistencia, llamadas y reenvío (CU-06, RF-39, RF-38) | ✅ (lectura) | ✅ |
| Reportes de ocupación, asistencia, demanda y caracterización (RF-27 a RF-30) | ✅ | ❌ |
| Listado de inasistencia reiterada (RF-24) | ✅ (todos los policlínicos) | ✅ (**solo su policlínico base**) |

- La TENS/TONS tiene un **policlínico base** (`usuario.id_policlinico`). Su agenda del día, su lista de espera, sus avisos y su listado de inasistencia se filtran por ese policlínico. Al reservar puede buscar cupos de cualquier policlínico, porque un paciente puede atenderse en otra sede.
- La Administración no tiene policlínico base (NULL) y elige el policlínico en los filtros.
- **Pantalla de ingreso P-00:** campos «Usuario» y «Contraseña» (obligatorios, con asterisco) y **un solo botón «Ingresar»**. El perfil (TENS/TONS o Administración) y el policlínico base salen de la cuenta, no se eligen: el backend autentica y el panel abre las vistas de ese perfil. Si las credenciales no son válidas, muestra un mensaje general sin revelar qué usuarios existen. Debajo va el enlace «← Volver al sitio de reserva de horas». *(Cambio del 21/11/2026 por la retroalimentación del curso: antes había dos botones, uno por perfil.)*
- Menú lateral de **TENS/TONS:** Agenda del día · Pacientes · Reservar cita · Reportes (solo «Inasistencia reiterada»). Abajo: nombre, «TENS/TONS · {policlínico}» y «Cerrar sesión».
- Menú lateral de **Administración:** Agenda de cupos · Policlínicos y profesionales · Reportes. Abajo: nombre, «Administración» y «Cerrar sesión».
- La API pública `/api/publico/**` no requiere sesión. Tiene un **límite de intentos** de identificación (5 fallidos por IP cada 10 minutos, con respuesta 429 genérica) y nunca devuelve datos de la ficha.

---

## 7. Requisitos funcionales y criterios de aceptación

Línea base v1.0: **RF-01 a RF-30 y RF-33 a RF-43**. Fuera de alcance: **RF-31** (adjuntar otros documentos; solo se implementan los certificados de RF-41) y **RF-32** (telemedicina). Cada CA es una prueba automatizada (sección 15).

### M1 · Policlínicos y profesionales (CU-01)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-01 | Registrar y modificar los policlínicos (nombre, dirección y comuna) y sus box. | CA-RF01-1: al registrar un policlínico con sus box, quedan seleccionables al definir bloques. |
| RF-02 | Registrar profesionales con su profesión y los policlínicos donde atienden (N:M), y si es alumno en práctica. | CA-RF02-1: el profesional queda disponible para bloques y hereda la duración de su profesión. |
| RF-03 | Credencial opcional: título, casa de estudios y registro de prestador (Superintendencia). | CA-RF03-1: la ficha muestra los tres datos. |
| RF-05 | Configurar la duración de cada profesión, incluida la de alumnos (entre 5 y 240 min). | CA-RF05-1: «psicólogo = 45» se guarda y se usa en los bloques nuevos. Rechaza 0 o un valor vacío (CU-01 E2). |

CU-01 E3: si la profesión no tiene duración, el profesional se guarda igual y se muestra el aviso «No se generarán cupos hasta definir la duración de {profesión}». En la tabla de duraciones aparece «Por definir».

### M2 · Agenda y cupos (CU-02)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-04 | Registrar y modificar bloques semanales: profesional, policlínico y box, día, hora de inicio, hora de término y vigencia (semestre). | CA-RF04-1: el bloque aparece en la agenda de esa semana. CA-RF04-2: al modificar un bloque con reservas, se muestran las afectadas antes de confirmar (D-10). |
| RF-06 | Generar automáticamente los cupos del bloque según la duración. | CA-RF06-1: médico de 09:00 a 10:00 genera 09:00, 09:20 y 09:40. CA-RF06-2: psicólogo de 09:00 a 10:30 genera 09:00 y 09:45. |
| RF-07 | Bloquear uno o varios cupos (un cupo o un rango horario) con motivo. Solo la Administración. | CA-RF07-1: el cupo bloqueado no aparece en la búsqueda y no se puede reservar. |
| RF-08 | Publicación gradual según el plazo de cada profesional. | CA-RF08-1: un cupo fuera del plazo no aparece; aparece al entrar al período. |
| RF-37 | Configurar el % de cupos web y el tope de reservas en línea por paciente. | CA-RF37-1: en la web solo se ofrece ese porcentaje de cupos; el resto queda para la TENS/TONS. |
| RF-40 | Ver la agenda por profesional o por box. | En M-02 «Vista por box» y en M-06 «Por box». |

**Generación de cupos (`BloqueHorario.generarCupos()`):** para cada fecha entre `vigencia_desde` y `vigencia_hasta` cuyo día de la semana coincida, se crean cupos desde `hora_inicio` en pasos de la duración, mientras `inicio + duración <= hora_termino`. El tramo que sobra no genera cupo. Antes de guardar, el panel muestra una **vista previa**: «Se generarán N cupos de X minutos: 09:00, 09:20 y 09:40. La duración la define la profesión (Medicina, 20 min)». Si no hay duración: «No se pueden generar cupos: {profesión} no tiene duración definida. Configúrala en Policlínicos y profesionales» (CU-02 E1). Si la hora de término no es posterior a la de inicio, se rechaza (E2). Si se superpone con otro bloque, se rechaza (E3, D-09).

**Cupos web (`ReglaReservaWeb`):** al generar los cupos de un bloque para una fecha, se marcan `habilitado_web = true` en `ceil(n × %/100)` de ellos, **repartidos de forma pareja** (no solo los primeros). Si la Administración cambia el %, se recalculan los cupos **futuros no reservados**. Si el % es NULL, no hay cupos web.

**Modificar un bloque (D-10):** si cambian el horario, el box o la vigencia, o si se desactiva, se calculan los cupos que dejarían de existir. Si alguno tiene cita vigente, `409` con la lista de afectadas. La interfaz muestra la lista y permite, para cada una, **reasignarla** a otro cupo disponible (anular y reservar, D-11) o **anularla con aviso** (origen ADMINISTRACION, mensaje ANULACION). Solo después se aplica el cambio. Los cupos sin cita que sobran se dejan como historial, sin borrar: se excluyen porque el bloque queda inactivo, o se marcan con un bloqueo de sistema con motivo «Bloque modificado». Documenta qué alternativa elegiste.

### M3 · Pacientes (CU-03 y la parte de pacientes de CU-08)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-09 | Buscar un paciente por documento (RUT con o sin puntos ni guion) y mostrar sus datos. | CA-RF09-1: se muestran los datos. CA-RF09-2: si no existe, el sistema lo indica y ofrece registrarlo. |
| RF-10 | Registrar un paciente: nombres, apellidos, tipo y número de documento, celular, correo (opcional), dirección, fecha de nacimiento, género y nacionalidad. | CA-RF10-1: se guarda sin correo y se encuentra por RUT. CA-RF10-2: un documento duplicado no se crea y se muestra el paciente existente. |
| RF-11 | Actualizar los datos del paciente (queda auditado). | CA-RF11-1: las confirmaciones siguientes van al número nuevo. |
| RF-12 | Adulto responsable (nombre, documento, teléfono y relación) si es menor o si un familiar gestiona la hora. Si el documento ya existe, se reutiliza. | CA-RF12-1: si es menor, se exige antes de guardar. |
| RF-13 | Registrar la verificación de FONASA y CESFAM: CESFAM de inscripción, fecha, FONASA vigente, inscrito en el CESFAM, resultado y observación. | CA-RF13-1: el paciente queda habilitado y se guardan el CESFAM y la fecha. El resultado es `HABILITADO` solo si ambas casillas están marcadas (`VerificacionRequisitos.calcularResultado()`). |
| RF-23 | Historial de citas con su estado (Próxima, Asistió, No asistió, Anulada y Pendiente de verificación). | CA-RF23-1: se listan con fecha, profesional y estado. |
| RF-35 | Identificar en línea con documento y fecha de nacimiento, **sin mostrar la ficha**. | CA-RF35-1: si la fecha no coincide, el sistema no avanza, no muestra datos y no indica si el RUT existe (ver sección 10.3). |
| RF-36 | Registrar en línea los datos de ingreso de un paciente nuevo, con `canal_registro = EN_LINEA` y sin verificación. | CA-RF36-1: queda registrado sin verificación y su cita queda «pendiente de verificación». |
| RF-41 | Subir los certificados de FONASA y CESFAM (foto o PDF; JPG, PNG o PDF; máximo 5 MB por archivo) en la reserva en línea. Solo el personal los ve. | La TENS/TONS los abre en el modal «Verificar reserva en línea» y la verificación queda ligada a esos documentos (`documento_adjunto.id_verificacion`). |

Al registrar un paciente (por la TENS/TONS o en línea) se informa: «Te enviaremos la confirmación y el recordatorio de tu hora por WhatsApp a este celular». Se guarda `fecha_aviso_mensajes = ahora` (A-01). No hay casilla de autorización.

Al registrar una verificación `HABILITADO`, **todas las citas `PENDIENTE_VERIFICACION` del paciente pasan a `RESERVADA`**. Si el resultado es `NO_CUMPLE`, la interfaz ofrece anular esas citas (D-03) y muestra: «No puede ser atendido: oriente su inscripción en el CESFAM».

Se calcula la edad («74 años»). El documento se muestra con formato `12.345.678-5`. Paciente extranjero: tipo PASAPORTE u OTRO, sin validar el dígito verificador.

### M4 · Citas (CU-04 y la reserva de CU-08)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-14 | Mostrar los cupos **disponibles** con filtros de policlínico, profesión, profesional (opcional) y rango de fechas. | CA-RF14-1: solo cupos disponibles (sin reservados, bloqueados ni no publicados). Se agrupan por día, con la mañana y la tarde, y hay un botón «Primera hora disponible». |
| RF-15 | Reservar un cupo para un paciente registrado y habilitado. | CA-RF15-1: el cupo deja de mostrarse. CA-RF15-2: un paciente sin verificación recibe 422 con el motivo. CA-RF15-3: si dos usuarios reservan a la vez, solo una reserva se concreta y el otro recibe 409 «Otra persona tomó este horario hace un momento. Elige otro». |
| RF-16 | Serie de sesiones: N citas semanales, mismo profesional, mismo día y misma hora, desde una primera fecha (2 a 30). | CA-RF16-1: se crean N reservas. D-06: si faltan cupos, primero se muestran las fechas sin cupo y la TENS/TONS decide. |
| RF-17 | Anular una reserva a pedido del paciente o de su adulto responsable, con motivo opcional. | CA-RF17-1: queda ANULADA y el cupo vuelve a estar disponible. |
| RF-34 | Reservar en línea un cupo disponible y `habilitado_web`. | CA-RF34-1: el cupo desaparece de la web y de la agenda de la TENS/TONS, y se envía la confirmación. |
| D-11 | Reagendar = anular + reservar en una transacción. | — |

**Reserva por la TENS/TONS (`AgendamientoService.reservar(idPaciente, idCupo, usuario)`), en una transacción:**
1. `ICupoRepository.obtenerParaReserva(idCupo)` hace `SELECT … FOR UPDATE` con `@Lock(PESSIMISTIC_WRITE)` o una consulta nativa.
2. Si el paciente no está habilitado, responde **422** «Este paciente aún no tiene registrada la verificación de FONASA y CESFAM. Regístrala en su ficha antes de reservar».
3. Si el cupo no está disponible, responde **409**.
4. Crea la `Cita` (canal TENS, RESERVADA, SIN_ENVIAR).
5. Programa los mensajes de CONFIRMACION (a ahora) y de RECORDATORIO y REFUERZOS (a futuro) **en la misma transacción**, con un registro por destinatario (D-07).
6. Registra la auditoría (CREAR, `cita`).
7. Si el paciente estaba en la lista de espera de esa profesión y ese policlínico, la interfaz ofrece retirarlo con motivo HORA_ASIGNADA.
8. Captura `DataIntegrityViolationException` sobre `uq_cita_cupo_activo` y responde 409 (segunda barrera). Los errores `SQLSTATE 45000` de los disparadores se traducen al mensaje correspondiente (422).

**Serie (`reservarSerie`):** calcula las N fechas (primera fecha + 7 días × i) y busca, para cada una, el cupo del mismo profesional a la misma hora. Una vista previa devuelve las fechas con cupo y las sin cupo. En la serie se pueden usar cupos todavía **no publicados** (más allá del plazo de 7 días), porque el profesional indica la serie. Esto es una decisión de implementación: anótala en `DECISIONES_IMPLEMENTACION.md`. Los cupos bloqueados o reservados nunca se usan. Al confirmar se crean la `serie_sesiones` y las citas con `id_serie`, cada una con su confirmación.

**Anular (`anular(idCita, origen, motivo, usuario)`):** marca la cita ANULADA (`fecha_anulacion`, `origen_anulacion`, `id_usuario_anulacion`), con lo que el cupo se libera solo. Cancela los mensajes PROGRAMADOS futuros de esa cita (el recordatorio no sale, CU-05 A2). Si fue la Administración o fue por un bloque, envía la plantilla ANULACION. Si hay inscripciones EN_ESPERA para la misma profesión y el mismo policlínico, crea un `aviso_cupo_liberado` (RF-26). Audita (ANULAR).

### M5 · Lista de espera (CU-04)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-25 | Inscribir a un paciente por policlínico y profesión cuando ningún cupo le acomoda. | CA-RF25-1: aparece con su fecha de inscripción, por orden de llegada. Se crea un movimiento INSCRIPCION. |
| RF-26 | Avisar a la TENS/TONS del policlínico cuando se libera un cupo de una atención con pacientes esperando. | CA-RF26-1: el aviso aparece **resaltado en amarillo** en la agenda del día: «Se liberó un cupo de {profesión} a las {hora}. Primera en la lista: {nombre}, {teléfono}». Tiene la acción «Reservar para {nombre}», que lleva a la reserva con ese cupo preseleccionado y marca el aviso como atendido. El sistema **no asigna el cupo solo**. |
| RF-42 | Adelantar a un paciente en la lista **con motivo obligatorio**. | Cambia `fecha_orden` para que quede donde la TENS/TONS indique (por defecto, primero) y registra el movimiento ADELANTO con motivo y usuario. CA-RF42-1: queda registrado quién lo hizo, cuándo y por qué. |
| RF-43 | Retirar de la lista con motivo: «Se le asignó una hora» (HORA_ASIGNADA), «Ya no necesita la atención» (DESISTE) o «No fue posible contactarla» (OTRO, con texto). | Estado RETIRADA y movimiento RETIRO. Los retirados no cuentan en la demanda. |

### M6 · Confirmaciones (CU-05, RF-38 y RF-39)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-19 | Enviar automáticamente la confirmación al completar la reserva. | CA-RF19-1: sale sin intervención. CA-RF19-2: si falla, la cita queda «confirmación no enviada» y por llamar. |
| RF-20 | Cada confirmación y recordatorio lleva el policlínico, su dirección, el profesional, la fecha y la hora. | CA-RF20-1. |
| RF-21 | Recordatorio **el día antes**, con botones «Asistiré» y «No podré asistir». | CA-RF21-1: sale al cumplirse el plazo. |
| RF-33 | «No podré asistir» anula la reserva. | CA-RF33-1: queda anulada y el cupo disponible (RN-07). Además se avisa a la lista de espera. |
| RF-38 | Refuerzos automáticos configurables y **reenvío manual** del recordatorio por la TENS/TONS. | Los refuerzos salen según los parámetros. El reenvío crea un mensaje con `enviado_por = TENS` e `id_usuario`. |
| RF-39 | Registrar el resultado de la llamada de confirmación: Confirmó que asistirá, Avisó que no asistirá (se anula la cita y se libera el cupo) o No contestó, más un comentario. | Queda en `llamada_confirmacion` y en el historial de la cita. |

Detalle en la sección 11.

### M7 · Asistencia (CU-06)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-18 | Agenda del día del policlínico: hora, paciente (con iniciales en un círculo), documento, profesional, profesión y box, estado de la confirmación y asistencia. | CA-RF18-1. Además (CAM-03) muestra el estado de confirmación de cada cita, incluida «No enviada», con el teléfono para llamar. |
| RF-22 | Registrar la asistencia: «Asistió» o «No asistió», **una sola vez**. | CA-RF22-1: se refleja en el historial y en los reportes sin otro traspaso (RNF-12). Una cita anulada no admite registro (CU-06 E1). |

### M8 · Reportes (CU-07)
| RF | Requisito | Criterios de aceptación |
|---|---|---|
| RF-24 | Listado de pacientes con un número de inasistencias en el período igual o mayor que el umbral. | CA-RF24-1: con umbral 3 en el mes, solo aparecen los que tienen 3 o más. Columnas: paciente, RUT, inasistencias, última inasistencia (fecha y profesión) y teléfono. |
| RF-27 | Ocupación: cupos ofrecidos (publicados y no bloqueados), reservados, % de ocupación y disponibles sin reservar, por policlínico y profesional. | CA-RF27-1: los totales coinciden con los registros. |
| RF-28 | Asistencia: citas reservadas, asistidas, inasistencias y % de inasistencia = inasistencias / (asistidas + inasistencias). | CA-RF28-1: por ejemplo, 347 / 2.318 = 15,0 %. También muestra la inasistencia por profesión (barras de color distinto si está sobre el promedio) y la evolución de los últimos 6 meses (línea). |
| RF-29 | Demanda por profesional = citas no anuladas en el período + inscripciones EN_ESPERA de su profesión en su policlínico (D-13). | CA-RF29-1: muestra por profesional las reservas y los pacientes en espera. |
| RF-30 | Caracterización de los pacientes atendidos (con al menos una cita ASISTIDA en el período): total, grupo etario (según `categoria_etaria`), género, nacionalidad (Chilena u otra), proporción de adultos y niños (<18) y % de adultos mayores (60 o más). | CA-RF30-1: los totales por categoría suman el total. |

Todos los reportes se calculan **con consultas sobre los registros vigentes** (RNF-12), sin tablas propias, detrás de `IConsultaReportes`. Filtros: período (mes o rango), policlínico y profesional. Si no hay datos: «No hay registros en el período seleccionado» (E2). Si el período no es válido, se rechaza (E1). Si no hay umbral configurado, se pide configurarlo (E3).

---

## 8. Requisitos no funcionales: cómo se cumplen

| RNF | Requisito | Implementación y verificación |
|---|---|---|
| RNF-01 | Reserva para un paciente registrado en 2 min o menos (buscar, elegir y reservar). | Buscador de RUT en la cabecera de todas las pantallas de la TENS/TONS, botón «Reservar cita» en la ficha, botón «Primera hora disponible» y confirmación en un clic. |
| RNF-02 | Lenguaje simple, sin abreviaturas ni tecnicismos («kine», «pol.»). | Revisa todas las plantillas y textos. En los mensajes al paciente: «Kinesiología», nunca «Kine». |
| RNF-03 | Funciona en los equipos de los 6 policlínicos. | SPA liviana, sin dependencias pesadas. Probada en Chrome y Edge actuales y con resolución de 1366×768 o más. |
| RNF-04 | Autenticación y perfiles. | Sección 6. Pruebas de acceso por perfil. |
| RNF-05 | Auditoría (usuario, fecha y hora) de reservas, anulaciones, verificaciones, adelantos, retiros, bloqueos y modificaciones. | `AuditoriaService.registrar(accion, entidad, id, detalle)`. Las acciones del sistema o del paciente en línea se guardan con `id_usuario` NULL. CA-RNF05-1. |
| RNF-06 | Mensajes sin información clínica. | Las plantillas solo contienen el nombre de pila, la fecha, la hora, la profesión, el profesional, el policlínico, el box y la dirección. CA-RNF06-1. |
| RNF-07 | 6 policlínicos simultáneos y 30.000 citas al año. | Índices del esquema. Escribe un generador de datos de carga de 12 meses (perfil `carga`). |
| RNF-08 | Búsqueda y reserva en 3 s o menos en el 95 % de los casos. | Consultas con índices, sin N+1 (usa proyecciones o `JOIN FETCH`). |
| RNF-09 | Confirmación en 5 min o menos desde la reserva. | El programador corre cada minuto. |
| RNF-10 y RNF-11 | Disponibilidad y respaldos diarios. | `restart: unless-stopped` en Docker y un script `scripts/respaldo.sh` (mysqldump diario) con su `restaurar.sh`. |
| RNF-12 | Reportes al día, sin traspasos. | Consultas en vivo. CA-RNF12-1: registrar una inasistencia y generar el reporte; ya debe aparecer. |

---

## 9. Diseño de clases (dominio y servicios)

Usa **estos nombres** (son los del informe de clases y de la arquitectura). Las entidades JPA se mapean a las tablas existentes con `@Table(name="…")` y `@Column(name="…")`.

**M1 catalogo:** `Policlinico`, `Box`, `Profesion` (`tieneDuracion()`, `duracionPara(esAlumno)`), `Profesional` (`duracionAtencion()`, `desactivar()`, asociación N:M con Policlinico mediante `profesional_policlinico`), `Credencial` (value object embebido: título, casa de estudios, registro), `CatalogoService`, `IPoliclinicoRepository`, `IProfesionRepository`, `IProfesionalRepository`.

**M2 agenda:** `BloqueHorario` (`generarCupos(duracion)`, `seSuperponeCon(otro)`), `Cupo` (`estado(hoy)`, `estaDisponible(hoy)`), `EstadoCupo` (enum), `Bloqueo`, `ReglaReservaWeb` (`cuposWeb(n)`, `superaTope(reservasActivasEnLinea)`), `AgendaService` (`crearBloque`, `modificarBloque`, `resolverAfectadas`, `bloquearCupos`, `agendaSemanal`, `agendaPorBox`, `configurarPublicacion`), `IBloqueRepository` (`buscarSuperpuestos`), `ICupoRepository` (`obtenerParaReserva` con bloqueo, `buscarDisponibles(FiltroCupos)`, `buscarDisponiblesWeb`).

**M3 pacientes:** `Paciente` (`edad()`, `esMenor()`, `estaHabilitado()`, `telefonosMensajeria()`), `DocumentoIdentidad` (VO: `tipo`, `numero`, `validar()` con el dígito verificador del RUT en módulo 11 solo si es RUT, `formatear()`, `normalizar()`), `AdultoResponsable`, `VerificacionRequisitos` (`calcularResultado()`), `DocumentoAdjunto`, `PacienteService` (`buscar`, `registrar`, `actualizar`, `registrarVerificacion`, `historial`, `identificarEnLinea`), `DocumentoService`, `IPacienteRepository`, `IAlmacenArchivos` (`guardar`, `abrir`).

**M4 citas:** `Cita` (`anular(origen, motivo, usuario, ahora)`, `registrarAsistencia(asistio, usuario, ahora)`, `marcarVerificada()`, `requiereLlamada(hoy)`), `EstadoCita`, `EstadoConfirmacion`, `CanalReserva`, `OrigenAnulacion`, `SerieSesiones`, `FiltroCupos` (VO), `AgendamientoService` (`consultarCupos`, `reservar`, `reservarEnLinea`, `previsualizarSerie`, `reservarSerie`, `anular`, `reagendar`), `ICitaRepository` (`buscarDelDia`, `buscarPorPaciente`, `contarActivasEnLinea`), implementadas por `CitaRepositorySQL` y `CupoRepositorySQL`. **Este es el detalle del patrón Repository: `AgendamientoService` solo conoce las interfaces.** Prueba sus reglas con repositorios en memoria.

**M5 listaespera:** `InscripcionEspera` (`adelantar(motivo, usuario)`, `retirar(motivo, detalle, usuario, cita?)`), `MovimientoEspera`, `MotivoRetiro`, `AvisoCupoLiberado` (`atender(usuario)`), `ListaEsperaService` (`inscribir`, `listar`, `adelantar`, `retirar`, `avisarCupoLiberado`, `avisosPendientes`), `IListaEsperaRepository`.

**M6 confirmaciones:** `PlantillaMensaje`, `Mensaje` (destinatario, teléfono de destino, estado de entrega, `marcarEnviado`, `marcarEntregado`, `marcarFallido`), `RespuestaPaciente`, `LlamadaConfirmacion`, `ReglaRecordatorio` (`momentoRecordatorio(cita)`, `momentosRefuerzo(cita)`), `NotificacionService` (`programarConfirmacion`, `programarRecordatorios`, `enviarPendientes`, `procesarEstado`, `procesarRespuesta`, `reenviarRecordatorio`, `registrarLlamada`, `enviarAnulacion`, `cancelarProgramados`), **`IServicioMensajeria`** (`enviarPlantilla(telefono, plantilla, parametros, botones) → idExterno`), **`WhatsAppBusinessAdapter`** (Adapter de WhatsApp Cloud API), `SimuladorMensajeriaAdapter`.

**M7 asistencia:** `AgendaDelDia` (VO: citas, `porLlamar()`, `resumen()`), `ResumenDia` (citas hoy, confirmadas, por llamar, sin respuesta, en lista de espera y reservas en línea pendientes de verificación), `VistaAgenda` (LISTA, POR_PROFESIONAL, POR_BOX), `AsistenciaService` (`agendaDelDia(policlinico, fecha, vista)`, `registrarAsistencia`).

**M8 reportes:** `Reporte` (abstracta: `FiltroReporte` con período, policlínico y profesional), `ReporteOcupacion`, `ReporteAsistencia`, `ReporteDemanda`, `ReporteCaracterizacion`, `ListadoInasistencia`, `CategoriaEtaria`, `ReporteService`, `IConsultaReportes` (implementada con SQL o JPQL).

**seguridad:** `Usuario` (`tienePermiso(operacion)`; regla: TENS/TONS con policlínico base y Administración sin él), `Perfil`, `AutenticacionService`, `RegistroAuditoria`, `AuditoriaService`, `ParametroSistema` (`validar()` según `tipo_dato`), `ParametroService`.

**compartido:** `Reloj` (inyectable, zona **America/Santiago**, para que las pruebas controlen «hoy»), `ManejadorErrores` (`@RestControllerAdvice`), `ProgramadorTareas`.

### 9.1 Notas de mapeo JPA y MySQL (importantes)
- `spring.jpa.hibernate.ddl-auto=validate`. **El esquema lo crean los scripts SQL**, no Hibernate. Puedes usar Flyway copiando `01` y `02` como `V1__esquema.sql` y `V2__reglas.sql`. En ese caso, quita `DROP/CREATE DATABASE` y `USE` en la copia de Flyway, y deja los originales intactos en `db/`. Los disparadores con `DELIMITER` no funcionan en Flyway: en `V2` escribe cada `CREATE TRIGGER` sin `DELIMITER` (Flyway acepta bloques `BEGIN…END` en MySQL).
- `cita.id_cupo_activo` es una columna **generada**: mapéala con `@Column(insertable=false, updatable=false)` o no la mapees.
- Los ENUM de MySQL se mapean con `@Enumerated(EnumType.STRING)` y enums de Java con exactamente los mismos valores.
- `BOOLEAN` es `TINYINT(1)`. Las horas son `LocalTime`, las fechas `LocalDate` y las fechas con hora `LocalDateTime`, con la zona de America/Santiago (`serverTimezone=America/Santiago`).
- La URL JDBC lleva `characterEncoding=UTF-8` y `connectionCollation=utf8mb4_spanish_ci`.
- Los errores de los disparadores llegan como `SQLException` con SQLState `45000`: tradúcelos a 422 con un mensaje simple.
- Usa `@Transactional` en los servicios de aplicación. La reserva usa el aislamiento por defecto (REPEATABLE READ) con `FOR UPDATE`.
- `parametro_sistema` es clave-valor: puedes **agregar filas** sin cambiar el esquema. Claves:
  - `PORCENTAJE_CUPOS_WEB` (PORCENTAJE, NULL)
  - `TOPE_RESERVAS_WEB` (ENTERO, NULL = sin tope)
  - `REFUERZOS_CANTIDAD` (ENTERO, NULL)
  - `REFUERZOS_HORAS_ANTES` (TEXTO, por ejemplo «48,4»: horas antes de la cita en que sale cada refuerzo)
  - `UMBRAL_INASISTENCIA` (ENTERO, 3)
  - Técnicas: `RECORDATORIO_HORA` (TEXTO «10:00»: hora del día anterior a la que sale el recordatorio) y `MENSAJE_REINTENTOS` (ENTERO, 3).

---

## 10. API REST

Formato JSON y fechas ISO (`2026-10-13`, `09:30`). Errores con el cuerpo `{ "codigo": "CUPO_TOMADO", "mensaje": "…en lenguaje simple…", "detalles": {…} }`.
Códigos: **400** validación (con los campos), **401** sin sesión, **403** sin permiso, **404**, **409** conflicto (cupo tomado, bloque superpuesto, reservas afectadas, documento duplicado), **422** regla de negocio (paciente sin verificación, tope web, profesión sin duración, menor sin adulto), **429** demasiados intentos.

### 10.1 Interna (con sesión) · prefijo `/api`
```
POST   /api/auth/ingresar                 {usuario, contrasena, perfil: "TENS"|"ADMINISTRACION"}
POST   /api/auth/salir
GET    /api/auth/yo                        → {nombre, perfil, policlinicoBase}

# M1 catálogo (ADMINISTRACION)
GET/POST        /api/policlinicos          · PUT /api/policlinicos/{id} · POST /api/policlinicos/{id}/box · PUT /api/box/{id}
GET             /api/profesiones           · PUT /api/profesiones/{id}/duracion {duracionMin, duracionAlumnoMin}
GET/POST        /api/profesionales         · GET/PUT /api/profesionales/{id} · POST /api/profesionales/{id}/desactivar

# M2 agenda (escritura: ADMINISTRACION)
GET    /api/agenda/semana?profesional=&policlinico=&desde=      → cupos con su estado derivado
GET    /api/agenda/semana-box?box=&desde=                       (RF-40)
POST   /api/bloques/previsualizar         → {cantidad, duracion, horas[]} | 422 sin duración
POST   /api/bloques                       · PUT /api/bloques/{id} (409 con las reservas afectadas) · POST /api/bloques/{id}/desactivar
POST   /api/bloques/{id}/resolver-afectadas  {acciones: [{idCita, accion: REASIGNAR|ANULAR, idCupoNuevo?}]}
POST   /api/bloqueos                      {idsCupo[] | {idProfesional, fecha, desde, hasta}, motivo}
GET/PUT /api/parametros                   · PUT /api/profesionales/{id}/publicacion {plazoPublicacionDias}
GET/PUT /api/categorias-etarias

# M3 pacientes (TENS; ADMINISTRACION solo lectura)
GET    /api/pacientes?documento=          → paciente | 404 {ofrecerRegistro:true}
POST   /api/pacientes                     · PUT /api/pacientes/{id}
GET    /api/pacientes/{id}                → datos + adulto responsable + verificación vigente + estado de habilitación
GET    /api/pacientes/{id}/historial
POST   /api/pacientes/{id}/verificaciones {fechaVerificacion, cesfamInscripcion, fonasaVigente, inscritoCesfam, observacion, idsDocumento[]}
GET    /api/pacientes/{id}/documentos     · GET /api/documentos/{id}/archivo (solo personal)

# M4 citas (TENS)
GET    /api/cupos/disponibles?policlinico=&profesion=&profesional=&desde=&hasta=
POST   /api/citas                         {idPaciente, idCupo}
POST   /api/citas/serie/previsualizar     {idPaciente, idProfesional, idCupoPrimero, cantidad} → {conCupo[], sinCupo[]}
POST   /api/citas/serie                   {…, fechasAceptadas[]}
POST   /api/citas/{id}/anular             {origen: "TENS", motivo?}
POST   /api/citas/{id}/reagendar          {idCupoNuevo}
GET    /api/citas/{id}                    → detalle con el historial (reserva, mensajes y estados, respuestas, llamadas, asistencia)

# M5 lista de espera (TENS)
GET    /api/lista-espera?policlinico=&profesion=
POST   /api/lista-espera                  {idPaciente, idPoliclinico, idProfesion}
POST   /api/lista-espera/{id}/adelantar   {motivo, posicion?}
POST   /api/lista-espera/{id}/retirar     {motivo: HORA_ASIGNADA|DESISTE|OTRO, detalle?, idCita?}
GET    /api/avisos-cupo                   · POST /api/avisos-cupo/{id}/atender

# M6 confirmaciones (TENS)
POST   /api/citas/{id}/reenviar-recordatorio
POST   /api/citas/{id}/llamadas           {resultado: CONFIRMO|NO_ASISTIRA|NO_CONTESTO, comentario?}

# M7 asistencia
GET    /api/agenda-dia?policlinico=&fecha=&vista=LISTA|POR_PROFESIONAL|POR_BOX   → {resumen, citas[] | columnas[]}
POST   /api/citas/{id}/asistencia         {asistio: true|false}

# M8 reportes
GET    /api/reportes/ocupacion|asistencia|demanda|caracterizacion?desde=&hasta=&policlinico=&profesional=   (ADMINISTRACION)
GET    /api/reportes/inasistencia-reiterada?desde=&hasta=&policlinico=      (TENS: se fuerza su policlínico)
```

### 10.2 Pública (sin sesión) · prefijo `/api/publico`
```
GET    /api/publico/policlinicos          → nombre, dirección y comuna (portada y fichas de los policlínicos)
GET    /api/publico/profesiones           → solo las que tienen duración
POST   /api/publico/identificacion        {tipoDocumento, numero, fechaNacimiento} → {token, estado: "IDENTIFICADO"|"NUEVO", documentoEnmascarado:"12.345.***-5"}
POST   /api/publico/pacientes             (token NUEVO) datos de ingreso + adulto responsable si es menor
GET    /api/publico/cupos?policlinico=&profesion=&profesional=&desde=     (solo disponibles + habilitado_web)
POST   /api/publico/reservas              (token) {idCupo} → comprobante
POST   /api/publico/reservas/{id}/certificados   multipart (token) tipo=CERTIFICADO_FONASA|CERTIFICADO_CESFAM
```
El `token` es aleatorio, guardado en el servidor (en memoria o en una tabla de caché; si usas tabla, no la agregues a las 26 del modelo: usa una caché en memoria con Caffeine). Dura 30 minutos y queda ligado al documento. **Ninguna respuesta pública incluye datos de la ficha**, salvo el documento enmascarado y el nombre de pila en el comprobante.

### 10.3 Identificación en línea sin revelar si el RUT existe (CA-RF35-1)
- Si el documento no es válido (dígito verificador del RUT), se responde 400: «El RUT no es válido. Revisa el número y el dígito verificador».
- Si el documento existe y la fecha coincide, el estado es `IDENTIFICADO`.
- Si el documento **no existe** o **existe con otra fecha de nacimiento**, la respuesta es **idéntica**: estado `NUEVO`, y la pantalla muestra «No encontramos una ficha con estos datos. Completa los datos una sola vez…».
- Si, al registrar los datos de un paciente `NUEVO`, el documento ya existe, se responde con un mensaje general: «No pudimos completar la reserva en línea con estos datos. Pide tu hora en tu policlínico o por teléfono». No se crea el duplicado, no se muestran datos y no se confirma que el documento exista.
- Cada intento fallido cuenta para el límite de 429. Anota esta decisión en `DECISIONES_IMPLEMENTACION.md`.

### 10.4 Reserva en línea (`AgendamientoService.reservarEnLinea`)
Misma transacción y bloqueo que la reserva interna, más estas condiciones: el cupo debe estar `habilitado_web`. Si `TOPE_RESERVAS_WEB` no es NULL y las citas en línea activas del paciente (RESERVADA o PENDIENTE desde hoy) son tope o más, responde 422: «Ya tienes {n} horas reservadas en línea. Para pedir otra, acércate a tu policlínico o llama por teléfono». Si el paciente no está habilitado, la cita queda **PENDIENTE_VERIFICACION** y el comprobante dice «Trae a tu primera atención: certificado de afiliación a FONASA, certificado de inscripción en tu CESFAM y tu RUT…». `id_usuario_reserva` = NULL y `canal_reserva` = EN_LINEA. Audita con usuario NULL.

### 10.5 Webhook
```
GET  /webhooks/whatsapp     (verificación de Meta: hub.mode, hub.verify_token, hub.challenge)
POST /webhooks/whatsapp     (estados sent/delivered/read/failed y respuestas de botones)
```
Verifica la firma `X-Hub-Signature-256` (HMAC-SHA256 con el App Secret) **antes** de procesar. Ignora los avisos repetidos usando `id_externo` (idempotencia). Responde 200 rápido.

---

## 11. Mensajería (M6) en detalle

### 11.1 Programación
Al reservar, **en la misma transacción**:
- **CONFIRMACION**: `fecha_programada = ahora`.
- **RECORDATORIO**: el día anterior a la cita a las `RECORDATORIO_HORA` (10:00). Si ese momento ya pasó (la cita es para hoy o mañana y la reserva es tardía), **no se programa** recordatorio.
- **REFUERZO**: según `REFUERZOS_CANTIDAD` y `REFUERZOS_HORAS_ANTES`, solo los que quedan en el futuro.
- **Un mensaje por destinatario:** el paciente y, si tiene, el adulto responsable (D-07). Se guarda `telefono_destino` al programar.

### 11.2 Envío (`@Scheduled(fixedDelay = 60_000)`)
`NotificacionService.enviarPendientes()` toma los `PROGRAMADO` con `fecha_programada <= ahora` cuya cita siga vigente (los de citas anuladas se descartan; no se borran, quedan con `estado_entrega = FALLIDO` y el detalle «cancelado: cita anulada», o documenta otra forma de marcarlos) y llama a `IServicioMensajeria.enviarPlantilla`. Si el envío funciona: ENVIADO, `fecha_envio` e `id_externo`. Si hay error, se reintenta en el siguiente ciclo hasta `MENSAJE_REINTENTOS` (lleva el conteo en memoria o en la auditoría; no agregues columnas). Agotados los reintentos: FALLIDO, y si era la confirmación, la cita pasa a `NO_ENVIADA` (por llamar).

### 11.3 Avisos de estado y respuestas
- Los avisos de estado actualizan `estado_entrega` (ENTREGADO, LEIDO, FALLIDO).
- La respuesta del botón «Asistiré» guarda una `respuesta_mensaje` y la cita pasa a CONFIRMADA.
- La respuesta «No podré asistir» guarda una `respuesta_mensaje`, llama a `AgendamientoService.anular(origen=PACIENTE_MENSAJE)`, envía la plantilla ANULACION («Gracias por avisar…») y genera el aviso de cupo liberado si corresponde.
- Vale la **última** respuesta.

### 11.4 Plantillas (seed de `plantilla_mensaje`; textos del prototipo M-05)
Variables: `{{nombre}}` (solo el nombre de pila), `{{fecha}}` («martes 13 de octubre»), `{{hora}}` («9:30»), `{{profesion}}`, `{{profesional}}` (sin título abreviado: «Tomás Pinto»), `{{policlinico}}`, `{{box}}`, `{{direccion}}`.

- **CONFIRMACION** (`hermandad_confirmacion`):
  «Hola, {{nombre}}. Su hora quedó reservada.
  {{Fecha}}, {{hora}}
  {{profesion}} con {{profesional}}
  Policlínico {{policlinico}}, {{box}}
  {{direccion}}
  Si no puede asistir, avísenos para dar la hora a otra persona.»
- **CONFIRMACION** para una cita pendiente de verificación (`hermandad_confirmacion_pendiente`): el mismo texto, más «Para su primera atención, traiga su certificado de FONASA y su certificado de inscripción en el CESFAM».
- **RECORDATORIO** (`hermandad_recordatorio`), con los botones **«Asistiré»** y **«No podré asistir»**:
  «Recuerde su hora de mañana:
  {{Fecha}}, {{hora}}
  {{profesion}}, Policlínico {{policlinico}}, {{box}}.
  ¿Podrá asistir?»
- **REFUERZO** (`hermandad_refuerzo`): el mismo texto y los mismos botones que el recordatorio, cambiando «mañana» por la fecha.
- **ANULACION** (`hermandad_anulacion`):
  «Gracias por avisar. Anulamos su hora del {{fecha}} a las {{hora}}.
  Si necesita una nueva hora, escriba o llame al policlínico.»
  Si la anulación la hizo el policlínico, se usa la variante `hermandad_anulacion_policlinico`: «Hola, {{nombre}}. Su hora del {{fecha}} a las {{hora}} fue anulada por el policlínico. Escriba o llame al policlínico para darle una nueva hora.»

Sin información clínica ni abreviaturas (RNF-02, RNF-06). Cuando el destinatario es el adulto responsable, `{{nombre}}` es el nombre de pila del paciente y el saludo cambia a «Hola. La hora de {{nombre}} quedó reservada…». En la agenda aparece «Avisado a Carla Herrera (madre)».

### 11.5 WhatsAppBusinessAdapter
Llama a WhatsApp Cloud API (`POST https://graph.facebook.com/v{versión}/{PHONE_NUMBER_ID}/messages`, `type: template`, con los componentes de cuerpo y los botones de respuesta rápida). Variables de entorno: `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` y `WHATSAPP_API_VERSION`. Se usa la cuenta de desarrollador del equipo, como aprobó la profesora. **Nunca subas credenciales al repositorio.**

### 11.6 Simulador (perfil por defecto: `mensajeria.proveedor=simulador`)
`SimuladorMensajeriaAdapter` implementa `IServicioMensajeria`: genera un `id_externo` falso y, unos segundos después, simula el aviso ENTREGADO llamando al mismo `NotificacionService.procesarEstado`. Si el teléfono termina en `0000`, simula un FALLIDO (para mostrar «No enviada: llamar»). Crea la página **`/simulador-whatsapp`**, que **solo existe en el perfil `demo` o `dev`**. Imita la pantalla M-05: un teléfono con la cabecera turquesa «Hermandad de Dolores · Mensajes automáticos de citas», un selector de número y las burbujas de los mensajes enviados a ese número, con los botones «Asistiré» y «No podré asistir». Los botones llaman a `POST /api/dev/simulador/respuesta`, que usa **el mismo** `NotificacionService.procesarRespuesta` que el webhook. Agrega también un botón «Forzar envío del recordatorio ahora» para la demo (`/api/dev/simulador/recordatorio/{idCita}`).

---

## 12. Interfaz (React + TypeScript) — fiel al Figma

### 12.1 Identidad visual (variables del Figma)
```css
--color-tinta:          #00687a;   /* turquesa institucional: botones primarios, enlaces, horas, ítem activo del menú */
--color-grafito:        #2b2f36;   /* texto principal */
--color-grafito-2:      #5b6472;   /* texto secundario */
--color-hoja:           #ffffff;   /* tarjetas */
--color-papel:          #f7f9fb;   /* encabezados de tabla, menú lateral */
--color-fondo:          #eef2f6;   /* fondo de la aplicación */
--color-renglon:        #dce3ea;   /* líneas de tabla («renglones» del cuaderno) */
--color-renglon-oscuro: #c3cdd8;   /* bordes de campos */
--color-margen:         #e06a5e;   /* margen rojo del cuaderno: SOLO en la columna de la hora y en la línea «Ahora 09:35» */
--color-resaltador:     #ffe58a;   /* amarillo: SOLO lo que requiere acción de una persona (no enviadas / por llamar, cupo liberado con lista de espera, pendiente de verificación) */
--color-exito:          #1f7a4d;   --color-exito-suave: #e5f3ea;   /* Confirmada, Asistió, Habilitada */
```
Agrega un rojo de error accesible (por ejemplo `#b42318` sobre `#fdecea`) para «No asistió» y los errores de validación, y un turquesa suave (`#e3f1f4`) para los avisos informativos y los cupos reservados en la agenda de cupos.

**Tipografía:** **Atkinson Hyperlegible Next** (cuerpo, pensada para baja visión) y **Bitter** (títulos y horas). Ambas en Google Fonts; inclúyelas localmente (paquetes `@fontsource`) para no depender de internet.
- Título: Bitter SemiBold 24/30. Hora: Bitter Medium 16/22 en color tinta.
- Cuerpo: Atkinson 16/24. Cuerpo fuerte: 600 16/24. Botón: 600 16/22. Etiqueta: Medium 14/20. Pequeño: 14/20.
- **Tamaño base de 16 px y botones de 48 px de alto.** Los botones y el menú llevan ícono y texto (usa `lucide-react`). Foco visible en todos los controles y contraste AA o mejor.

**Elementos de cuaderno (sobrios):** las tablas tienen líneas como renglones, y la columna de la hora tiene a su derecha una línea vertical roja (`--color-margen`). En la agenda del día va la línea horizontal roja «Ahora 09:35». En P-00, la mitad izquierda es una hoja con renglones y margen rojo, con el logo.

**Logo:** «Instituto de Caridad Hermandad de Dolores · +200 Años al servicio de Chile». Está en el Figma (descárgalo con el MCP de Figma si lo tienes y déjalo en `frontend/public/logo.png`). Si no lo tienes, usa un marcador de texto con esas palabras y deja un TODO en `DECISIONES_IMPLEMENTACION.md` para que el equipo agregue el archivo.

**Etiquetas de estado (chips):** Confirmada (verde suave), No enviada (amarillo resaltador), Sin respuesta (gris), Confirmada por teléfono (verde), Pendiente de verificación (amarillo), Habilitada / Habilitada para reservar (verde), Próxima (turquesa suave), Asistió (verde), No asistió (rojo suave), Anulada (gris).

### 12.2 Diseño general del panel
Menú lateral fijo (≈248 px) con el logo, el subtítulo «Agenda de atenciones», los ítems del perfil y el pie con el usuario y «Cerrar sesión». Cabecera de cada pantalla: título en Bitter, subtítulo gris y, a la derecha, el buscador «Buscar paciente por RUT» con el botón «Buscar» (en las pantallas de la TENS/TONS). Contenido en tarjetas blancas con radio de 12 px sobre `--color-fondo`. Mira `docs/pantallas/M-06_agenda_del_dia.png`.

### 12.3 Pantallas y rutas (todas están en `docs/PANTALLAS_FIGMA.md` con sus textos)
**Pública — `src/reserva-en-linea`**
| Ruta | Pantalla (Figma) | Contenido |
|---|---|---|
| `/` | P-00 Portada pública (`242:1584`) | Cabecera con logo, «Reservar hora», «Requisitos», «Policlínicos» e «Ingreso del personal». Héroe «Reserva tu hora de atención» con el texto de apoyo, los botones «Reservar hora» y «Ver requisitos» y la nota «¿Prefieres hacerlo en persona?…». Tarjeta «Así de simple» con 3 pasos. Sección «Antes de reservar» (certificado de FONASA, inscripción en tu CESFAM, RUT; nota de menores; «¿Es tu primera atención?…»). Sección «Nuestros policlínicos»: los 6 con dirección y «Ver →». Pie. Para la imagen del héroe usa una ilustración o un fondo neutro, **no fotos de personas reales**. |
| `/policlinicos/:id` | Ficha del policlínico (`246:1601`…`246:1686`) | Nombre, dirección, comuna, profesiones que atiende y botón «Reservar hora aquí». |
| `/reservar` | M-08 (sección H) | Asistente de 3 pasos: **1 Identificación**, **2 Elegir hora**, **3 Confirmación**. Cabecera «Reserva de horas en línea · ¿Prefieres ayuda? Pide tu hora en tu policlínico o por teléfono · Ingreso del personal». |
| | Identificación (`222:8965`) | Tipo de documento (RUT por defecto), número y fecha de nacimiento, más los textos de apoyo y privacidad. Error «RUT no válido» (`222:9130`). |
| | Paciente nuevo: datos (`222:9168`, menor `222:9399`) | «No encontramos una ficha con estos datos…»; nombre completo, celular, dirección, género, nacionalidad y correo opcional. «¿La persona es menor de edad?» se calcula con la fecha y, si lo es, pide los datos del adulto responsable. Aviso de WhatsApp. |
| | Elegir hora (`222:9006`) | Filtros de policlínico, profesión y profesional (opcional, «Cualquiera»). Cupos web agrupados por día. Panel «Tu hora» y el botón «Reservar esta hora». Indicador «Paciente identificado · RUT 12.345.***-5». |
| | Certificados (`222:9450`, `222:9523`) | Solo para pacientes no habilitados: «Subirlos ahora» (foto o PDF, «Quitar», «Agregar otro archivo (JPG, PNG o PDF)», «Solo el personal del policlínico podrá ver estos documentos») o «Los llevaré a mi primera atención». |
| | Hora reservada (`222:9059`) / Pendiente de verificación (`222:9268`, `222:9573`) / Tope de reservas (`222:9303`) | Comprobante con policlínico, dirección, profesional, fecha y hora (RF-20). Si está pendiente: «Trae a tu primera atención…». |
| | Versión celular (`222:9358`, `222:9384`) | Los mismos pasos en una columna (el diseño es adaptable). |

**Panel del personal — `src/panel`**
| Ruta | Perfil | Pantalla (Figma) | Puntos clave |
|---|---|---|---|
| `/ingreso` | — | P-00 Ingreso (`222:1622`) | Sección 6. |
| `/agenda-dia` | TENS (Administración: lectura) | M-06 Agenda del día (`222:1814`) | Selector «Lista · Por profesional · Por box». 5 tarjetas de resumen: Citas hoy · Confirmadas · **Por llamar (no enviadas)** en amarillo · Sin respuesta · En lista de espera con «Ver lista →». Chip amarillo «N reservas en línea pendientes de verificación». Tabla con Hora, Paciente (iniciales y documento), Profesional (y profesión y box), Confirmación (chip; «Llamar al +56 9 …» si no se envió; enlace «Gestionar confirmación»; «Avisado a … (madre)») y Asistencia (botones «Asistió», «No asistió» y «Anular», o el registro hecho «✓ Asistió»). Línea roja «Ahora HH:MM». Fila amarilla del cupo liberado con lista de espera (`222:2650`). Navegación por día (‹ › «Hoy»). |
| | | Por profesional (`222:2922`) | Columnas por profesional (nombre, profesión, box y duración) y filas por hora. Tarjetas de cita con el estado de confirmación. Los cupos libres aparecen como «+ 10:00 Disponible»: al hacer clic se abre el **panel lateral de reserva** (`222:3173`) con búsqueda de paciente por RUT. Los bloqueos aparecen como «🔒 Reunión clínica». Al hacer clic en una cita se abre el **detalle con su historial** (`222:3466`). |
| | | Por box (`260:1586`) | Igual que por profesional, pero con columnas por box (RF-40). |
| | | Modal «Anular cita» (`222:2639`) | «¿Anular la cita de {nombre}?», el detalle, el motivo opcional y Cancelar / Anular cita. |
| | | Modal «Gestionar confirmación» (`222:4352`) | Mensajes enviados y su estado, «Reenviar recordatorio por WhatsApp» (RF-38), «Registrar resultado de la llamada» con 3 opciones y comentario (RF-39), y la nota «Refuerzos automáticos: los define la Administración». |
| | | Modal «Verificar reserva en línea» (`222:4038`) | Documentos adjuntos con «Ver documento», casillas FONASA y CESFAM, CESFAM y Registrar. |
| `/lista-espera` | TENS | M-06 Lista de espera (`222:4720`) | N.º, paciente, atención solicitada, «Inscrito el» y las acciones «Adelantar» (excepto el primero) y «Quitar de la lista». Modales con motivo (`222:4855`, `222:4863`). |
| `/pacientes` | TENS | M-03 Buscar (`222:5141`), RUT no registrado (`222:6356`) | Buscador grande. Si no existe: «No encontramos un paciente con el RUT …» y «Registrar paciente». |
| `/pacientes/nuevo`, `/pacientes/:id/editar` | TENS | M-03 Registrar (`222:6549`, datos faltantes `222:6482`), Editar (`222:5965`) | Los campos obligatorios faltantes se marcan en rojo. Si es menor, se exige el adulto responsable. |
| `/pacientes/:id` | TENS | M-03 Ficha (`222:5197`), Recién registrado (`222:6615`), Reserva no permitida (`222:6722`), Habilitado (`222:6850`) | Nombre en Bitter, RUT y edad, chip de habilitación, «Editar datos» y «Reservar cita» (deshabilitado o con aviso si no está habilitado). Tarjetas: Datos de ingreso, Requisitos de ingreso (verificación registrada: «FONASA y CESFAM Norte verificados el 02/09/2026 por Carolina Muñoz (TENS/TONS)», o el botón «Registrar verificación» que abre el modal `222:6829`) e Historial de citas. |
| `/reservar` | TENS | M-04 Reservar cita (`222:5318`, `222:5422`) | Tarjeta del paciente con chip «Habilitada» y «Cambiar paciente». Filtros de policlínico, profesión, profesional (opcional) y fechas, con «Buscar cupos». «Horarios disponibles» en columnas por día (Mañana y Tarde) y «Primera hora disponible». Panel «Resumen de la reserva» con profesional, lugar, fecha y hora (duración), la casilla «Reservar como serie de sesiones», el aviso «Confirmación automática: se enviará al +56 9 … apenas confirmes» y «Confirmar reserva». Abajo: «¿Ningún horario le acomoda? Inscríbela en la lista de espera de {profesión}…». Estados: Cita reservada (`222:5548`), Sin cupos (`222:5628`), Inscrita en lista de espera (`222:5707`), Horario tomado (`222:5863`), Serie (`222:6153`, `222:6275`). |
| `/agenda-cupos` | Administración | M-02 (`222:7043`, `222:7157`, `222:7277`, `222:7394`, `222:7515`, `222:7637`) | Filtros de profesional, policlínico y semana. Botones «Publicación y reglas» y «+ Nuevo bloque horario». Leyenda de estados (Reservado, Bloqueado, Disponible, No publicado con borde punteado). Columnas lunes a viernes (incluye sábado si hay bloques) con los cupos. Panel lateral «Nuevo bloque horario» con vista previa. Modal «Bloquear cupo u horario» (`222:7500`) con motivo. Modal «Publicación y reglas de reserva» (`222:7621`): plazo por profesional («Hasta 1 semana antes»), «Cupos ofrecidos en la reserva en línea: N % de cada bloque», «Tope de horas en línea por paciente (opcional): Sin tope» y, agrégalo aquí, los **refuerzos** (cantidad y horas antes) y el **umbral de inasistencia**. Vista por box. |
| `/catalogo` | Administración | M-01 (`222:7759`, `222:7895`, `222:7979`, `222:8045`, `222:8113`, `222:8179`, `222:8321`) | Pestañas: Profesionales (listado con la credencial), Policlínicos y box, Duración por profesión («Por definir» y modal «Modificar duración de …»). Formulario «Registrar profesional» con la validación de campos faltantes y el aviso de la duración por definir. |
| `/reportes` | Administración (TENS: solo inasistencia) | M-07 (`222:8334`…`222:8895`) | Pestañas: Ocupación · Asistencia · Demanda · Caracterización · Inasistencia reiterada. Filtros de período, policlínico y profesional, con «Generar reporte». Tarjetas de indicadores con su comparación con el mes anterior, barras horizontales y línea de evolución (usa **Recharts**, con **colores distintos por serie**, como recomendó el Certamen 1). Estado «Sin datos» (`222:8837`). La TENS/TONS ve solo su policlínico (`222:8895`). |
| `/simulador-whatsapp` | solo demo y dev | basado en M-05 (`222:6955`…) | Sección 11.6. |

**Comportamiento común:** los formularios validan en el cliente (zod + react-hook-form) **y** en el servidor. Los mensajes de error son simples y van junto a cada campo. Los textos de cada pantalla son los de `docs/PANTALLAS_FIGMA.md`. Las fechas van en formato chileno («Martes 13 de octubre de 2026», «13/10/2026», «09:30»). Usa TanStack Query para la API. Cuando algo cambia (reserva, anulación o asistencia), invalida las consultas de la agenda, los cupos y la ficha.

---

## 13. Datos de demostración (perfil `demo`, generados en Java)

Crea un `DatosDemoSeeder` (`CommandLineRunner`, perfil `demo`, que se ejecuta solo si la base está vacía). Las fechas son **relativas a hoy**, para que la demo siempre tenga agenda. Usa **solo datos ficticios**; los nombres y RUT vienen del prototipo y todos tienen el dígito verificador correcto.

- **Perfiles:** Administración y TENS/TONS.
- **Usuarios** (contraseña de demo `Hermandad2026!`, documentada solo en el README del repositorio):
  - `l.martinez`: Laura Martínez, Administración.
  - `c.munoz`: Carolina Muñoz, TENS/TONS de Casa Central.
  - Una TENS/TONS más por cada policlínico (`tens.santateresa`, etc.).
- **Policlínicos:** los 6 de la sección 1.2. Casa Central con Box 1 a Box 5 y los demás con Box 1 a Box 3.
- **Profesiones y duración (min):**
  - Medicina 20
  - Kinesiología 30
  - Odontología 30
  - Obstetricia (matrón) 30
  - Psicología 45
  - Fonoaudiología, Nutrición y Podología: NULL (por definir)

  Deja `duracion_alumno_min` en NULL y anota en `DECISIONES_IMPLEMENTACION.md` que se cargan los valores de la planilla «Estándar de atención por profesional» cuando el equipo la entregue.
- **Profesionales (Casa Central):**
  - Dra. Paula Rojas: Medicina, Box 1, de lunes a viernes de 09:00 a 12:00, con una «Reunión clínica» bloqueada a las 11:20 el día de hoy.
  - Dr. Andrés Vidal: Medicina, Box 5.
  - Klgo. Tomás Pinto: Kinesiología, Box 2.
  - Dra. Camila Soto: Odontología, Box 3.
  - Ps. Diego Vera: Psicología, Box 4.
  - Mat. Ana Fuentes: Obstetricia, también en Santa Teresa.
  - Una fonoaudióloga sin duración, para mostrar CU-01 E3.

  Agrega 1 o 2 profesionales con sus bloques en cada uno de los otros policlínicos. La vigencia de los bloques es el semestre actual.
- **Parámetros:**
  - `PORCENTAJE_CUPOS_WEB` = 50 (en la demo; en producción parte NULL)
  - `TOPE_RESERVAS_WEB` = NULL
  - `REFUERZOS_CANTIDAD` = 1 y `REFUERZOS_HORAS_ANTES` = «4»
  - `UMBRAL_INASISTENCIA` = 3
  - `RECORDATORIO_HORA` = «10:00»
  - `MENSAJE_REINTENTOS` = 3
- **Categorías etarias:** «0 a 17 años», «18 a 59 años» y «60 años o más».
- **Plantillas:** las de la sección 11.4.
- **Pacientes** (con agenda de hoy en Casa Central, como en M-06):
  - María González Pérez, 12.345.678-5, 74 años, +56 9 1234 5678. Adulto responsable: Pedro González, hijo, +56 9 2222 3333. Habilitada (CESFAM Norte, verificada el 02/09 por Carolina Muñoz). Historial: asistió, asistió, no asistió, anulada y una cita próxima.
  - Juan Soto Rivas, 9.876.543-3, +56 9 8765 4321. Confirmación **no enviada**: usa un teléfono que termine en 0000 en el simulador o carga el mensaje FALLIDO.
  - Rosa Díaz Muñoz, 7.654.321-6, +56 9 7123 4567. Sin respuesta.
  - Pedro Lagos Vidal, 15.432.109-8. Asistió.
  - Elena Torres Pino, 18.765.432-7.
  - Martín Herrera Lagos, 23.456.789-6. Menor de edad; adulto responsable: Carla Herrera, madre. Confirmada.
  - Luis Campos Reyes, 10.203.040-0. Sin respuesta y 3 inasistencias.
  - Ana Reyes Fuentes, 21.098.765-7. No enviada.
  - Tomás Fuentes Vega, 24.567.890-8, 9 años, nacido el 14/03/2017. Adulto responsable: Valentina Vega Rojas, madre, RUT 16.987.654-1. **Sin verificación** (muestra «Reserva no permitida»).
  - Rosa Elena Muñoz Díaz: registrada en línea, con una cita PENDIENTE_VERIFICACION y certificados adjuntos (archivos PDF y JPG de ejemplo generados).
  - Para el listado de inasistencia: Jorge Ibarra Soto, 11.223.344-K (4); Héctor Molina Paz, 8.765.432-K (3); Camila Rivas Ortiz, 19.283.746-4 (3).
- **Lista de espera de Casa Central:** Gloria Fuentes Lagos (Medicina), Raúl Ortega Vidal (Kinesiología), Marta Cifuentes Rey (Psicología), Teresa Aguilar Rojas (Medicina), Benjamín Silva Núñez (Odontología) y Ana Morales Pinto (Medicina).
- **Historia de 6 meses:** genera citas pasadas en todos los policlínicos (asistidas en un 80 a 85 % y el resto inasistidas o anuladas) para que los reportes tengan datos parecidos a los del prototipo (ocupación ≈ 91 % e inasistencia ≈ 15 %).

---

## 14. Docker y ejecución

`docker-compose.yml`:
- **mysql** (`mysql:8.0`): `MYSQL_ROOT_PASSWORD` y `TZ=America/Santiago`. Monta `db/01` y `db/02` en `/docker-entrypoint-initdb.d/` en ese orden (o deja que Flyway cree el esquema; elige una sola vía y documéntala). Volumen persistente y *healthcheck*. Crea un usuario de aplicación `agenda` con permisos solo sobre `hermandad_agenda`.
- **backend** (con un Dockerfile multi-etapa de Maven y JRE 21): perfiles `SPRING_PROFILES_ACTIVE=demo`, la conexión a la base y las variables de WhatsApp (opcionales) y de almacenamiento (`ALMACEN_DIRECTORIO=/datos/certificados`, en un volumen). Depende de `mysql` sano.
- **frontend** (multi-etapa: build de Node y Nginx): sirve la SPA (con *fallback* a `index.html`) y hace proxy de `/api/` y `/webhooks/` a `backend:8080`. Publica el puerto 8080 del host, o el 80.

Comandos que deben funcionar y quedar documentados en el README del repositorio:
```
cp .env.example .env
docker compose up --build        # → http://localhost:8080 (portada) y /ingreso (personal)
cd backend && ./mvnw test         # pruebas (Testcontainers MySQL 8.0)
cd frontend && npm test           # pruebas de componentes (Vitest + Testing Library)
```

---

## 15. Pruebas (obligatorias)

### 15.1 Backend
- **Unitarias del dominio**, sin Spring:
  - `generarCupos` (CA-RF06-1 y CA-RF06-2, el tramo sobrante y el alumno en práctica).
  - `seSuperponeCon`.
  - `Cupo.estado` (las 4 reglas y su prioridad).
  - `DocumentoIdentidad.validar` (RUT válido, inválido, con K, con y sin puntos; pasaporte sin validación).
  - `VerificacionRequisitos.calcularResultado`.
  - Las transiciones de `Cita`.
  - `ReglaReservaWeb` (porcentaje con reparto parejo y tope).
  - `ReglaRecordatorio`.
- **De servicios con repositorios en memoria** (esto demuestra el patrón Repository): `AgendamientoService.reservar`, `reservarEnLinea`, `anular` (con aviso de lista de espera) y `reservarSerie` (D-06).
- **De integración con Testcontainers (MySQL 8.0)**, con `01` y `02` cargados: un caso por cada CA de la sección 7, más:
  - **Concurrencia (CA-RF15-3):** dos hilos reservan el mismo cupo a la vez (uno por la TENS y otro en línea). Resultado esperado: una sola cita vigente; el otro recibe 409.
  - **Permisos (CA-RNF04-1 y CA-RNF04-2):** la TENS/TONS intenta crear un bloque y recibe 403; sin sesión, 401.
  - **Auditoría (CA-RNF05-1):** al anular queda un registro con usuario, fecha y acción.
  - **Mensajería:** reserva, luego mensaje PROGRAMADO, luego `enviarPendientes` con el simulador y queda ENVIADA. Una falla con reintentos termina en NO_ENVIADA. La respuesta NO_ASISTIRA anula la cita y genera el aviso. El webhook con firma inválida responde 401 o 403. El mismo `id_externo` repetido se ignora.
  - **Reserva en línea:** documento inexistente o con otra fecha de nacimiento: la misma respuesta (CA-RF35-1). Paciente nuevo: cita PENDIENTE (CA-RF36-1). Cupo no web: rechazo. Tope: 422.
  - **Reportes:** las fórmulas de CA-RF27-1 a CA-RF30-1 sobre un conjunto de datos conocido. RNF-12: registrar una inasistencia y generar el reporte; debe incluirla.
- **Prueba de arquitectura** (ArchUnit) con las reglas de capas y módulos.

### 15.2 SQL (`db/04_pruebas.sql`, con `db/03_datos_prueba_hermandad.sql`)
Las 10 operaciones que el modelo **debe rechazar** (del informe de Diseño de Datos), más el caso permitido:
1. Reservar un cupo que ya tiene una cita vigente: `uq_cita_cupo_activo`.
2. Reservar para un paciente que no existe: `fk_cita_paciente`.
3. Que la TENS/TONS reserve para un paciente sin verificación: disparador (CA-RF15-2).
4. Crear un bloque superpuesto para el mismo profesional: disparador (D-09).
5. Crear un bloque con la hora de término antes que la de inicio: `ck_bloque_horas`.
6. Registrar a un menor sin adulto responsable: disparador (RN-05).
7. Registrar dos veces el mismo documento: `uq_paciente_documento`.
8. Adelantar en la lista de espera sin motivo: `ck_mov_motivo`.
9. Anular una cita sin fecha ni origen de anulación: `ck_cita_anulacion`.
10. Registrar una profesión con duración 0: `ck_profesion_duracion`.
- **Caso permitido:** al anular bien una cita, `id_cupo_activo` queda en NULL y el cupo se puede reservar de nuevo (RN-07). Una reserva en línea sin verificación queda PENDIENTE_VERIFICACION (RN-13).

### 15.3 Frontend
Pruebas de los flujos clave con Vitest y Testing Library (MSW para simular la API):
- Reservar (el recorrido A del prototipo).
- Registrar un paciente menor sin adulto, con el error.
- Registrar la asistencia.
- Anular con aviso de lista de espera.
- Reserva en línea con un paciente nuevo.
- Permisos del menú por perfil.

Si puedes, agrega un e2e con Playwright del recorrido A y del H.

---

## 16. Seguridad y privacidad (RES-06, Ley 21.719)

- Contraseñas con BCrypt. Nunca se registran contraseñas, tokens ni datos personales completos en los logs: el documento va enmascarado.
- HTTPS en producción (Nginx). En local, HTTP.
- La API pública está separada (`/api/publico`), con límite de intentos y respuestas genéricas, y nunca muestra la ficha.
- Los certificados se guardan fuera de la base, en un directorio no público con nombre aleatorio (UUID). Solo se pueden descargar con sesión del personal. Se valida el tipo MIME real (por los *magic bytes*) y el tamaño.
- La firma del webhook se verifica.
- Mínimo de datos en los mensajes (RNF-06).
- Auditoría de todas las operaciones sensibles.
- Nada se borra (D-17).
- Los secretos van en `.env`, que está en `.gitignore`. `.env.example` lleva valores de ejemplo.

---

## 17. Orden de construcción (EDT 1.4, con MVP primero)

| Fase | Paquete de la EDT | Alcance | Cómo se comprueba que está terminada |
|---|---|---|---|
| 0 | — | Repositorio, Docker, base de datos con 01 y 02, esqueleto de Spring y React, diseño base (tokens, fuentes, menú) e ingreso por perfil. | `docker compose up` levanta todo; el ingreso funciona y los permisos dan 401 o 403 donde corresponde. |
| 1 | 1.4.1 | M1 catálogo, M2 agenda (bloques, cupos, vista previa, bloqueos, publicación, % web, vista por box), parámetros y auditoría. | CA-RF01 a RF08, CA-RF37 y CA-RNF04 en verde. Pantallas M-01 y M-02 iguales al Figma. |
| 2 | 1.4.2 | M3 pacientes (sin la parte en línea), M4 citas (reservar, anular, reagendar, serie) y agenda del día en lista. | CA-RF09 a RF17 y la prueba de concurrencia. Pantallas M-03 y M-04 y la base de M-06. |
| 3 | 1.4.3 | M6 confirmaciones: simulador, programador, plantillas, recordatorio, refuerzos, respuestas, webhook y adapter de WhatsApp. | CA-RF19 a RF21, RF33 y RF38. Página `/simulador-whatsapp`. **Hito: MVP integrado** (agenda digital + confirmación automática de punta a punta). |
| 4 | 1.4.5 | M7 asistencia (las 3 vistas, resumen, por llamar, llamada RF-39, modal de confirmación), M5 lista de espera (inscribir, aviso, adelantar, retirar) y M8 reportes. | CA-RF18, RF22 a RF30, RF39, RF42, RF43 y RNF12. Pantallas M-06 y M-07. |
| 5 | 1.4.6 | CU-08: portada pública, fichas de los policlínicos, asistente de reserva en línea, registro en línea, certificados y verificación desde la agenda. | CA-RF34 a RF36, RF41 y CA-RF35-1 (sin revelar la existencia del documento). Pantallas P-00 y M-08, incluida la de celular. |
| 6 | 1.5.1 | Pruebas completas, datos demo de 6 meses, generador de carga, scripts de respaldo, documentación, `TRAZABILIDAD.md` y revisión de textos (RNF-02). | Lista de verificación de la sección 19. |

---

## 18. Fuera de alcance (no implementar)

- Telemedicina (RF-32).
- Adjuntar otros documentos, como la orden médica o de derivación (resto de RF-31). Solo se implementan los certificados de FONASA y CESFAM.
- Interoperabilidad con otros sistemas (RES-07).
- Conservar la hora original al reagendar (D-11, versión futura).
- Encuesta de satisfacción y sobrecupo (pedidos del Certamen 1, que cambiarían RN-02). Quedan para una versión futura.
- SMS (D-15).
- Priorización de pacientes (RN-06).
- Autoagendamiento con cuentas de paciente. La reserva en línea es sin cuenta.
- Información clínica (RES-08).
- Inteligencia artificial.

---

## 19. Lista de verificación final (Definition of Done)

- [ ] `docker compose up --build` levanta MySQL, el backend y el frontend sin pasos manuales, y los datos demo cargan.
- [ ] El esquema de la base es **exactamente** el de `db/01` + `db/02` (26 tablas, 5 disparadores) y `ddl-auto=validate` pasa.
- [ ] Están implementados todos los RF de la sección 7 (RF-01 a RF-30 y RF-33 a RF-43), y **cada CA tiene su prueba automatizada en verde**.
- [ ] La prueba de concurrencia (CA-RF15-3) pasa de forma estable (al menos 20 repeticiones).
- [ ] Las 10 operaciones inválidas de `db/04_pruebas.sql` se rechazan y el caso permitido funciona.
- [ ] Los permisos por perfil cumplen la tabla de la sección 6 (pruebas de 401 y 403).
- [ ] Cada reserva, anulación, verificación, adelanto, retiro, bloqueo y modificación queda en `auditoria`.
- [ ] Ninguna operación borra citas, inscripciones, mensajes, bloques ni profesionales.
- [ ] Recorrido completo en la demo: la TENS reserva, la confirmación aparece en `/simulador-whatsapp`, el paciente responde «No podré asistir», la cita se anula, y la agenda muestra el cupo liberado con el primero de la lista de espera.
- [ ] Reserva en línea completa con un paciente nuevo: la cita queda PENDIENTE, la TENS la verifica con los certificados y la cita pasa a RESERVADA.
- [ ] Las pantallas coinciden con el Figma v3 en estructura, textos, colores, tipografía, tamaños (16 px y botones de 48 px) y estados. Compáralas con `docs/pantallas/*.png`.
- [ ] No hay textos con abreviaturas ni tecnicismos para el paciente (RNF-02) ni datos clínicos en los mensajes (RNF-06).
- [ ] La reserva en línea funciona en el ancho de un celular (390 px).
- [ ] `docs/TRAZABILIDAD.md` relaciona cada RF con su caso de uso, sus clases, su endpoint, su pantalla y su prueba.
- [ ] `docs/DECISIONES_IMPLEMENTACION.md` lista toda decisión tomada que no estaba en esta especificación.
- [ ] El README del repositorio explica cómo ejecutar, los usuarios demo, cómo activar WhatsApp real y cómo hacer y restaurar el respaldo.
