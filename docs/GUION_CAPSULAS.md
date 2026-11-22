# Guion de las cápsulas (entrega del 23-11-2026)

Basado en `02_Guia_Capsula_Aplicacion_y_Pitch.pdf` y en las rúbricas del video tutorial y del pitch.
Formato: MP4, mínimo 720p, voz clara. Nombres: **Capsula_Aplicacion** y **Capsula_Pitch**.

## Antes de grabar (checklist)

- [ ] AWS Academy → **Start Lab** y esperar ~3 minutos. Abrir https://100-59-97-56.sslip.io y confirmar que carga.
- [ ] Celular con WhatsApp a mano (el número verificado en Meta) y, si se puede, duplicado en pantalla (WhatsApp Web) para que se vea en el video.
- [ ] Borrar la reserva de prueba anterior para empezar limpio: `scripts/borrar-paciente-prueba.sh 20.431.749-6 --nube --borrar`.
- [ ] Navegador en pantalla completa, zoom 110 %, sin pestañas personales ni notificaciones.
- [ ] Grabador: OBS, el grabador de Windows (Win+Alt+R) o Loom, a 1080p.

---

## Cápsula 1: Tutorial de la aplicación (~10 minutos)

La rúbrica pide: introducción (10 %), acceso (10 %), recorrido por la interfaz (15 %), **uso paso a paso (20 %)**,
ejemplos reales (10 %), claridad (10 %), calidad técnica (10 %), consejos y cierre (5 %) y cumplimiento (10 %).

| Min. | Sección | Qué mostrar | Qué decir (idea) |
|---|---|---|---|
| 0:00–0:45 | **Presentación** | Portada pública | «Este es el sistema de agenda de atenciones de los 6 policlínicos de la Hermandad de Dolores. Reemplaza el cuaderno y las planillas por una agenda común y confirma las horas por WhatsApp. Lo usan las TENS/TONS, la Administración y los pacientes para reservar en línea.» |
| 0:45–1:30 | **Acceso** | URL en el navegador → «Ingreso del personal» | «Está en la nube, en esta dirección con HTTPS. El personal entra con su usuario; el sistema reconoce el perfil y muestra solo sus funciones. No se elige el rol.» Entrar como `c.munoz`. |
| 1:30–2:30 | **Navegación (TENS)** | Menú: Agenda del día, Pacientes, Reservar cita, Reportes | Mostrar los indicadores (citas de hoy, confirmadas, por llamar, sin respuesta, lista de espera) y las vistas Lista / Por profesional / Por box. |
| 2:30–5:00 | **Flujo principal: reservar** | Reservar cita → RUT → policlínico y profesión (campos con *) → elegir hora → Confirmar | Explicar que solo aparecen cupos libres. Al confirmar: **aviso flotante** + **vista del WhatsApp** que recibe el paciente. **Mostrar el celular: llega la confirmación real.** |
| 5:00–6:30 | **Recordatorio y respuesta** | Agenda del día (fecha de la cita) → la cita → «Gestionar confirmación» → «Reenviar recordatorio por WhatsApp» | Llega el recordatorio con los botones. Apretar **«No podré asistir»** → llega el mensaje de anulación → en la agenda la cita aparece anulada y el **cupo liberado se ofrece a la lista de espera** (aviso amarillo). |
| 6:30–7:30 | **Validación relevante** | Reserva en línea con un RUT existente y fecha de nacimiento equivocada | Mensaje genérico: el sistema **no revela si el RUT existe** (protección de datos). Otra: campos obligatorios vacíos → mensajes junto a cada campo. |
| 7:30–8:15 | **Reserva en línea (paciente)** | Portada → Reservar hora → datos → hora → certificados | Queda «pendiente de verificación» hasta que la TENS revise los certificados FONASA y CESFAM. |
| 8:15–9:15 | **Administración** | Cerrar sesión → entrar como `l.martinez` → Agenda de cupos → Reportes | Bloques horarios y publicación; Reportes: ocupación, **asistencia (anillo, evolución)**, demanda y caracterización. |
| 9:15–10:00 | **Resultado y cierre** | Ficha del paciente con su historial | «La cita quedó registrada con su historial de mensajes y la auditoría.» **Recomendaciones:** mantener actualizado el celular del paciente, revisar cada mañana la lista «Por llamar», y verificar los certificados de las reservas en línea. |

**Consejos:** hablar más lento de lo normal, no mostrar contraseñas en pantalla (escribirlas antes de grabar), y cortar
los tiempos de espera en la edición. Usar datos ficticios o el propio RUT del equipo con su consentimiento.

---

## Cápsula 2: Pitch (2 a 3 minutos)

La rúbrica pide: problema (15 %), solución (15 %), **demostración (20 %)**, estructura (10 %), comunicación (10 %),
tiempo (5 %), calidad (10 %), impacto (10 %) y coherencia con el informe (5 %).

| Tiempo | Contenido | Guion sugerido |
|---|---|---|
| 0:00–0:30 | **Problema** | «Los 6 policlínicos de la Hermandad de Dolores agendan en cuadernos, fichas de papel y planillas Excel distintas. Las confirmaciones se hacen llamando uno por uno, y cerca del 15 % de los pacientes no llega: esas horas se pierden y otros pacientes siguen esperando.» |
| 0:30–1:00 | **Solución y valor** | «Construimos una agenda digital común para los 6 policlínicos, que confirma cada hora por WhatsApp automáticamente y, si el paciente avisa que no puede ir, libera el cupo y avisa a quien está en lista de espera. La TENS deja de llamar a todos: solo llama a quien no responde.» |
| 1:00–1:45 | **Demostración** | Reservar una hora → llega el WhatsApp al celular → recordatorio → «No podré asistir» → el cupo aparece liberado para la lista de espera. (Grabación acelerada y sin pausas.) |
| 1:45–2:30 | **Resultado, limitación y cierre** | «El MVP está en la nube y funciona con WhatsApp real. La principal limitación: usamos el número de prueba de Meta; para operar se necesita el número oficial de la Hermandad y verificar la organización en Meta. Con esto, cada hora que hoy se pierde puede volver a usarse.» |

**Consejos:** abrir con una cifra (15 % de inasistencia, 6 policlínicos), mirar a la cámara si aparece el equipo, y
ensayar con cronómetro: la rúbrica descuenta si se pasa del tiempo.
