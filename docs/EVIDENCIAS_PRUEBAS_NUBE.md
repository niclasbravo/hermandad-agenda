# Evidencias de pruebas en la nube

- **Fecha:** 22-11-2026 01:48  ·  **Entorno:** nube, https://100-59-97-56.sslip.io (AWS Academy Learner Lab, EC2 t3.small, perfil demo)  ·  **Versión:** commit `fcdcdeb`
- **Ejecución:** `python scripts/evidencias_nube.py` (reproducible). Datos ficticios; las citas de prueba se anulan al final.

| Evidencia | CP | RF/RNF | Prueba | Resultado esperado | Estado | Resultado obtenido |
|---|---|---|---|---|---|---|
| EV-CP-53 | CP-53 | RNF-04 | Consultar la agenda del día sin sesión iniciada. | El sistema no muestra información (CA-RNF04-2). | **Aprobada** | HTTP 401 SIN_SESION: no se entregan datos. |
| EV-CP-52 | CP-52 | RNF-04 | Usuario TENS/TONS intenta ver el reporte de ocupación (función de Administración). | El sistema lo impide (CA-RNF04-1, RN-03). | **Aprobada** | TENS/TONS: HTTP 403. Administración: HTTP 200. |
| EV-CP-11 | CP-11 | RF-09 | Buscar por RUT a un paciente ficticio registrado (15.432.109-8). | Se muestran sus datos (CA-RF09-1). | **Aprobada** | HTTP 200: ficha de «Pedro Lagos Vidal», habilitado=True. |
| EV-CP-12 | CP-12 | RF-09 | Buscar un RUT ficticio válido no registrado (11.111.111-1). | El sistema lo indica y ofrece registrarlo (CA-RF09-2). | **Aprobada** | HTTP 404: «No encontramos un paciente con el documento 11.111.111-1.» (ofrecerRegistro=true). |
| EV-CP-18 | CP-18 | RF-14 | Buscar cupos de Medicina en Casa Central en las próximas dos semanas. | Se listan solo los cupos disponibles (CA-RF14-1). | **Aprobada** | HTTP 200: 62 cupos, estados ['DISPONIBLE']. |
| EV-CP-19 | CP-19 | RF-15 | Reservar el primer cupo para el paciente ficticio habilitado. | El cupo deja de mostrarse como disponible (CA-RF15-1). | **Aprobada** | HTTP 201: cita 11963 el 2026-11-23 09:00; el cupo ya no aparece en la búsqueda. |
| EV-CP-21 | CP-21 | RF-15 | Dos solicitudes simultáneas reservan el mismo cupo para pacientes distintos. | Solo una reserva se concreta; el otro usuario recibe un aviso (CA-RF15-3). | **Aprobada** | Códigos [201, 409]; aviso al segundo: «Otra persona tomó este horario hace un momento. Elige otro.». |
| EV-CP-25 | CP-25 | RF-19 | Al reservar (CP-19) se programa y envía la confirmación sin intervención de la TENS/TONS. | Se envía la confirmación automáticamente (CA-RF19-1). | **Aprobada** | Mensaje hermandad_confirmacion en estado ENTREGADO (destino ficticio → simulador; envío real a número del equipo: ver EV-WA-01). |
| EV-CP-24 | CP-24 | RF-18 | Consultar la agenda del día de la cita reservada en CP-19. | Se listan las citas con hora, profesional y paciente (CA-RF18-1). | **Aprobada** | HTTP 200: 19 citas el 2026-11-23; incluye la cita 11963: 09:00, Paula Rojas, Pedro Lagos Vidal. |
| EV-CP-43 | CP-43 | RF-38 | Reenviar manualmente el recordatorio de la cita de CP-19. | El reenvío queda registrado (CA-RF38). | **Aprobada** | HTTP 200: «Recordatorio reenviado por WhatsApp. Saldrá en menos de un minuto.»; recordatorios de la cita: 2. |
| EV-CP-38 | CP-38 | RF-33 | El paciente responde «No podré asistir» al recordatorio (mismo servicio que usa el webhook). | La reserva queda anulada y el cupo vuelve a estar disponible (CA-RF33-1). | **Aprobada** | Respuesta HTTP 200; cita 11963: ['ANULADA', 'PACIENTE_MENSAJE']; el cupo volvió a la búsqueda: True. |
| EV-CP-54 | CP-54 | RNF-05 | Revisar la auditoría de la cita creada y anulada. | El registro muestra usuario, fecha, hora y operación (CA-RNF05-1). | **Aprobada** | Registros: CREAR por usuario 2 el 2026-11-22 01:45:38; ANULAR por usuario sistema el 2026-11-22 01:48:13 |
| EV-CP-40 | CP-40 | RF-35 | Reserva en línea: identificarse con un RUT registrado y otra fecha de nacimiento. | No muestra datos de la ficha ni indica si el RUT existe (CA-RF35-1). | **Aprobada** | HTTP 200: estado NUEVO, sin nombre ni datos de la ficha. |
| EV-CP-57 | CP-57 | RNF-08 | Medir 20 búsquedas de cupos y 20 consultas de agenda en la nube (t3.small). | 95 % o más responden dentro de 3 s (valor propuesto). | **Aprobada** | 40 solicitudes: mediana 0.56 s, p95 0.62 s, 100 % bajo 3 s. |

**Resumen:** 14 de 14 casos aprobados.

## Evidencias manuales del 21 y 22-11-2026 (nube, WhatsApp real)

| Evidencia | CP | RF/RNF | Prueba | Resultado obtenido | Estado |
|---|---|---|---|---|---|
| EV-WA-01 | CP-25, CP-27 | RF-19, RF-20 | Reserva en línea con el número del equipo (verificado en Meta) y plantilla `hermandad_confirmacion_pendiente` aprobada. | Meta aceptó el envío (id `wamid.HBgL…`, mensaje 283, 22-11 00:16) y llegó al celular con policlínico, dirección, profesional, fecha y hora. | Aprobada |
| EV-WA-02 | CP-43 | RF-38 | Reenvío del recordatorio con botones «Asistiré» / «No podré asistir» al número del equipo. | Mensajes 286 a 288 aceptados por Meta y recibidos en el celular con los dos botones. | Aprobada |
| EV-WA-03 | CP-26 (parcial) | RF-19 | Verificación del webhook por Meta y avisos de entrega. | Meta verificó `GET /webhooks/whatsapp` (HTTP 200 con el desafío) y, tras suscribir la app a la cuenta, el aviso `delivered` marcó el mensaje 288 como ENTREGADO. Token falso → HTTP 403. | Aprobada |
| EV-CP-60 | CP-60 | RNF-11 | Respaldo con `scripts/respaldo.sh` y restauración en una base aparte. | Respaldo en 1,4 s (329 KB). Restaurado en 5 s: 26 tablas, 5 disparadores, 920 pacientes, 11.959 citas y 188 mensajes, idénticos al original. Respaldo programado al encender y a las 02:00. | Aprobada |
| EV-P00 | — | RNF-04 | Ingreso con un solo botón (corrección de P-00). | `c.munoz` entra como TENS/TONS (Casa Central) y `l.martinez` como Administración, sin elegir el perfil. | Aprobada |
| EV-AUT-01 | Varios | — | Pruebas automáticas del backend (dominio, servicios, ArchUnit, plantillas, números permitidos). | 95 pruebas en verde (`mvn test`, sin integración). | Aprobada |
| EV-AUT-02 | Varios | — | Pruebas automáticas del frontend (Vitest + Testing Library + MSW). | 13 pruebas en verde (`npm test`). | Aprobada |

**Pendientes (sin evidencia, no se marcan como aprobados):** la respuesta del botón desde el celular hasta el webhook
(se probó por el mismo servicio en CP-38; falta la captura con la app publicada), CP-42 (porcentaje de cupos web),
CP-49/51/58/59 (aceptación con usuarias y piloto) y CP-56 (carga de 30.000 citas).
