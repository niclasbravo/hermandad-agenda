# Decisiones de implementación

Decisiones tomadas durante la construcción que no estaban en la especificación (`docs/ESPECIFICACION.md`). Se aplicó siempre la regla más conservadora.

## Infraestructura y datos

| # | Decisión | Motivo |
|---|---|---|
| DI-01 | El esquema se crea **solo** con los scripts de `db/` montados en `/docker-entrypoint-initdb.d` (sin Flyway). `db/00_usuario_aplicacion.sh` crea el usuario `agenda` con permisos solo sobre `hermandad_agenda`. | La sección 14 pide una sola vía. Los scripts originales (con `DELIMITER`) se usan sin copias. |
| DI-02 | Las columnas `TINYINT/SMALLINT UNSIGNED` se mapean a `Integer` con `columnDefinition`, para que `ddl-auto=validate` pase sin cambiar el esquema. | Esquema intacto. |
| DI-03 | La columna generada `cita.id_cupo_activo` no se mapea en JPA; las consultas que la necesitan usan SQL nativo (`CupoRepositorySQL`). | Sección 9.1. |
| DI-04 | Las entidades JPA se relacionan entre módulos con `@ManyToOne` (por ejemplo, `Cita → Paciente`), pero cada módulo accede a los datos de otro solo por su servicio o por una interfaz de dominio. Las vistas de lectura que cruzan módulos (agenda del día, reportes) usan consultas en su propia `infraestructura`. | Pragmatismo sin romper las reglas de dependencia (ArchUnit). |
| DI-05 | `FiltroCupos` está en `agenda.dominio` (no en `citas`), porque lo usa `ICupoRepository`. | Evitar que `agenda` dependa de `citas`. |
| DI-06 | Los mensajes programados de una cita anulada quedan `FALLIDO` con `fecha_envio` = momento de la cancelación (el CHECK `ck_mensaje_envio` exige fecha fuera de `PROGRAMADO`) y la auditoría registra «cancelado: cita anulada» (`entidad = mensaje`). En el historial de la cita se muestran como «cancelado (cita anulada)». | Sección 11.2 permite documentar otra forma; no se agregan columnas. |
| DI-07 | El conteo de reintentos de envío vive en memoria (se pierde al reiniciar: el mensaje vuelve a tener 3 intentos). | Sección 11.2: sin columnas nuevas. |
| DI-08 | Las acciones de auditoría usan los valores del ENUM existente: asistencia y cambios de confirmación → `MODIFICAR`; llamada → `CREAR` sobre `llamada_confirmacion`; cambio de parámetros → `MODIFICAR` con `id_registro = 0` y la clave en el detalle. | El ENUM `accion` no tiene más valores. |
| DI-09 | Los parámetros iniciales de producción (sección 9.1) y las 6 plantillas se crean al arrancar si no existen, en cualquier perfil. | Sin ellos el sistema no puede programar mensajes. |
| DI-10 | Si la base no tiene usuarios y existen `ADMIN_INICIAL_USUARIO` y `ADMIN_INICIAL_CONTRASENA`, se crea la primera cuenta de Administración. | Producción sin datos demo necesita un primer acceso. |

## Agenda y cupos

| # | Decisión | Motivo |
|---|---|---|
| DI-11 | Se agrega el estado derivado `PASADO` para cupos libres cuya hora ya pasó. | La especificación no define ese caso; no son «disponibles». |
| DI-12 | **Modificar un bloque (D-10).** Si solo cambia la vigencia, se ajusta el mismo bloque. Si cambian el día, el horario o el box, el bloque actual se **cierra** (vigencia hasta ayer, o se desactiva si no había empezado) y se crea uno nuevo desde hoy. En ambos casos los cupos futuros que sobran **no se borran**: quedan con un bloqueo de sistema con motivo «Bloque modificado». Si alguno tiene cita vigente, responde 409 con las reservas afectadas, que se resuelven con `POST /api/bloques/{id}/resolver-afectadas` antes de aplicar el cambio. | Conserva el historial de box y horarios de las citas pasadas. |
| DI-13 | Al crear un bloque, los cupos se generan **desde hoy** (o desde el inicio de la vigencia si es futura). | Cupos en el pasado inflarían el reporte de ocupación. |
| DI-14 | No se puede bloquear un cupo que tiene una cita vigente: hay que anularla o reasignarla primero (409). | Regla conservadora; D-12 no lo define. |
| DI-15 | `PUT /api/bloques/{id}/resolver-afectadas` está en el controlador del módulo de citas (misma ruta de la especificación). | Evita que `agenda` dependa de `citas`. |
| DI-16 | En una **serie** se aceptan cupos todavía no publicados (más allá del plazo), porque la serie la indica el profesional. Los bloqueados, reservados y pasados nunca se usan. | Indicado en la sección 7 (M4). |
| DI-17 | Los cupos web se reparten por bloque y fecha con los índices `floor(i × n / k)`. | «Repartidos de forma pareja». |

## Citas, mensajes y lista de espera

| # | Decisión | Motivo |
|---|---|---|
| DI-18 | Al reagendar o reasignar se conserva el canal de la cita original (una cita en línea sigue siendo en línea). No se envía aviso de anulación al reagendar: llega la nueva confirmación. | Evitar mensajes contradictorios. |
| DI-19 | La anulación hecha por la TENS/TONS (el paciente avisó por otro canal) no envía mensaje. La de la Administración o por bloque envía `hermandad_anulacion_policlinico`; la del paciente por WhatsApp envía `hermandad_anulacion`. | Sección 7 (M4) y 11.3. |
| DI-20 | El aviso de cupo liberado solo se crea si la hora del cupo aún no pasó, y solo se muestra mientras el cupo siga libre y haya alguien esperando. | Un cupo pasado no se puede reasignar. |
| DI-21 | Un paciente no puede tener dos inscripciones `EN_ESPERA` para la misma profesión y policlínico (409). | Regla conservadora. |
| DI-22 | Al adelantar, la nueva `fecha_orden` es la del puesto destino menos 1 segundo. | `DATETIME` tiene resolución de segundos. |
| DI-23 | La cita queda «No enviada» solo si **todos** los destinatarios de la confirmación fallaron (paciente y adulto responsable, D-07). | Si el adulto la recibió, la familia está avisada. |
| DI-24 | Estado «Sin respuesta»: se asigna cuando se envía el recordatorio o un refuerzo y la cita no está confirmada. | Sección 4.2. |
| DI-25 | Variante para el adulto responsable: el texto cambia a «Hola. La hora de {nombre}…». Con WhatsApp real esto requeriría plantillas aprobadas adicionales; hoy se envía la misma plantilla con los mismos parámetros. **TODO del equipo:** aprobar las variantes en Meta. | Sección 11.4. |
| DI-26 | Plantilla REFUERZO: «Recuerde su hora del {fecha}:» seguido del mismo cuerpo del recordatorio. | «Cambiando "mañana" por la fecha». |
| DI-27 | La TENS/TONS puede anular citas de cualquier policlínico (puede reservar en otras sedes), pero solo registra asistencia y maneja la lista de espera de su policlínico base. | Sección 6. |

## Reserva en línea y seguridad

| # | Decisión | Motivo |
|---|---|---|
| DI-28 | **Identificación sin revelar si el RUT existe (CA-RF35-1).** Documento inexistente o con otra fecha de nacimiento → misma respuesta `NUEVO`. Cada respuesta `NUEVO` y cada documento inválido cuentan como intento fallido para el límite (5 por IP cada 10 minutos → 429 genérico). Si al registrar los datos el documento ya existe → mensaje general «No pudimos completar la reserva en línea…», sin crear duplicados. | Sección 10.3. |
| DI-29 | El token de la reserva en línea va en la cabecera `X-Token-Reserva`; vive 30 minutos en memoria (Caffeine) y queda ligado al documento y la fecha de nacimiento. Los certificados solo se pueden subir para la cita creada con ese token y mientras esté pendiente de verificación. | Sección 10.2. |
| DI-30 | Detrás de Nginx, la IP del límite de intentos se toma de `X-Real-IP` (el backend no se expone directamente). | Docker Compose. |
| DI-31 | La pantalla de ingreso no muestra el campo «Policlínico» ni pide elegir el perfil: hay un solo botón «Ingresar» y el perfil y el policlínico base salen de la cuenta (el policlínico aparece en el menú tras ingresar). | Retroalimentación del curso (21/11) y no revelar qué usuarios existen antes de autenticar. |
| DI-32 | El webhook rechaza todo (401) si `WHATSAPP_APP_SECRET` no está definido. | Firma obligatoria. |
| DI-33 | Las respuestas repetidas del webhook se ignoran con un caché en memoria del id del mensaje entrante (48 horas); los avisos de estado son idempotentes porque no retroceden (LEIDO no vuelve a ENTREGADO). | Sección 10.5. |

## Interfaz

| # | Decisión | Motivo |
|---|---|---|
| DI-34 | Ruta de «Reservar cita» del panel: `/reservar-cita` (la especificación usaba `/reservar` para ambas; `/reservar` quedó para la reserva en línea pública). | Conflicto de rutas. |
| DI-35 | Los profesionales se muestran con nombre y apellido, sin «Dra.», «Klgo.» ni «Ps.» (el esquema no guarda el tratamiento y RNF-02 pide evitar abreviaturas). | RNF-02. |
| DI-36 | **Logo:** se usa el logo oficial de la Hermandad en `frontend/public/logo.png` (componente `Logo` de `frontend/src/compartido/componentes.tsx`), en tres tamaños. | Sección 12.1. |
| DI-37 | El formulario en línea pide «Nombres» y «Apellidos» por separado (el prototipo tenía «Nombre completo») porque el esquema los guarda separados. | Esquema. |
| DI-38 | Opciones de género: Femenino, Masculino, Otro, Prefiere no decir. Relación del adulto: lista cerrada (madre, padre, hijo, hija, abuela…). **Validar con la mandante** las categorías de la ficha de ingreso. | El esquema dice «según la ficha de ingreso de la institución». |
| DI-39 | El período de los reportes se elige por mes (por defecto, el mes actual) o por rango. La comparación es con el período anterior de igual largo. | Sección 7 (M8). |
| DI-40 | Reportes, Agenda de cupos, Catálogo y Simulador se cargan bajo demanda (*lazy*) para que la SPA inicial sea liviana. | RNF-03. |

## Datos de demostración

| # | Decisión | Motivo |
|---|---|---|
| DI-41 | `duracion_alumno_min` queda en NULL. **TODO:** cargar los valores de la planilla «Estándar de atención por profesional» cuando el equipo la entregue. | Sección 13. |
| DI-42 | Los bloques demo rigen desde el primer día del mes de hace 6 meses hasta el fin del semestre actual, para que exista historia. Si hoy es sábado o domingo, la agenda del día del prototipo se arma para el lunes siguiente. | Sección 13. |
| DI-43 | La historia (900 pacientes ficticios) ocupa ≈ 95 % de los cupos pasados; de esas citas, 4 % anuladas, 81,6 % asistidas y 14,4 % inasistidas → ocupación ≈ 91 % e inasistencia ≈ 15 %. Los RUT de los adultos responsables del prototipo (no definidos) se generan con dígito verificador válido. | Sección 13. |
| DI-44 | Perfil `carga`: 12 meses, 4.000 pacientes y bloques de tarde adicionales en los 6 policlínicos. | RNF-07. |
