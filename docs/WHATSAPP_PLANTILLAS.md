# Plantillas de WhatsApp para Meta

Cómo registrar en Meta (WhatsApp Manager) las 6 plantillas que usa el sistema. El backend envía, para cada
plantilla, **solo las variables que aparecen en su texto y en el orden en que aparecen** (`RedactorMensajes.parametros`).
Por eso en Meta cada variable se escribe como `{{1}}`, `{{2}}`, ... en ese mismo orden.

## Dónde se crean

developers.facebook.com → tu app → **WhatsApp → Plantillas de mensajes** (o business.facebook.com → WhatsApp Manager →
Administrar plantillas) → **Crear plantilla**.

En todas:

- **Categoría:** Utilidad (*Utility*).
- **Nombre:** exactamente el indicado (minúsculas y guion bajo).
- **Idioma:** Español (`es`). No elijas «Español (Chile)» ni otra variante: el backend pide `es`.
- **Encabezado y pie:** ninguno.
- Copia el **cuerpo** tal cual, con sus saltos de línea. En «Muestras de variables» usa los ejemplos de la tabla.

Si Meta rechaza una plantilla por «demasiadas variables para la longitud del mensaje», avisa al equipo: hay que
alargar el texto fijo en Meta **y** en la tabla `plantilla_mensaje`, porque el orden de las variables debe coincidir.

---

### 1. `hermandad_confirmacion` (al reservar)

```
Hola, {{1}}. Su hora quedó reservada.
{{2}}, {{3}}
{{4}} con {{5}}
Policlínico {{6}}, {{7}}
{{8}}
Si no puede asistir, avísenos para dar la hora a otra persona.
```

| Variable | Dato | Ejemplo |
|---|---|---|
| {{1}} | nombre de pila | Rosa |
| {{2}} | fecha larga | Martes 24 de noviembre de 2026 |
| {{3}} | hora | 9:20 |
| {{4}} | profesión | Medicina |
| {{5}} | profesional | Paula Rojas |
| {{6}} | policlínico | Casa Central |
| {{7}} | box | Box 1 |
| {{8}} | dirección | Av. Ejemplo 123, Santiago |

### 2. `hermandad_confirmacion_pendiente` (reserva en línea de paciente nuevo)

Mismo cuerpo y variables que la anterior, con una línea más al final:

```
Hola, {{1}}. Su hora quedó reservada.
{{2}}, {{3}}
{{4}} con {{5}}
Policlínico {{6}}, {{7}}
{{8}}
Si no puede asistir, avísenos para dar la hora a otra persona.
Para su primera atención, traiga su certificado de FONASA y su certificado de inscripción en el CESFAM.
```

### 3. `hermandad_recordatorio` (el día antes), **con botones**

```
Recuerde su hora de mañana:
{{1}}, {{2}}
{{3}}, Policlínico {{4}}, {{5}}.
¿Podrá asistir?
```

| Variable | Dato | Ejemplo |
|---|---|---|
| {{1}} | fecha larga | Martes 24 de noviembre de 2026 |
| {{2}} | hora | 9:20 |
| {{3}} | profesión | Medicina |
| {{4}} | policlínico | Casa Central |
| {{5}} | box | Box 1 |

**Botones:** «Respuesta rápida», en este orden: `Asistiré` y `No podré asistir`.
El backend asigna la carga `ASISTIRA` al primero y `NO_ASISTIRA` al segundo.

### 4. `hermandad_refuerzo` (refuerzo si no respondió), **con botones**

```
Recuerde su hora del {{1}}:
{{2}}, {{3}}
{{4}}, Policlínico {{5}}, {{6}}.
¿Podrá asistir?
```

| Variable | Dato | Ejemplo |
|---|---|---|
| {{1}} | fecha larga (minúscula) | martes 24 de noviembre de 2026 |
| {{2}} | fecha larga | Martes 24 de noviembre de 2026 |
| {{3}} | hora | 9:20 |
| {{4}} | profesión | Medicina |
| {{5}} | policlínico | Casa Central |
| {{6}} | box | Box 1 |

**Botones:** igual que el recordatorio: `Asistiré` y `No podré asistir`.

### 5. `hermandad_anulacion` (cuando el paciente responde «No podré asistir»)

```
Gracias por avisar. Anulamos su hora del {{1}} a las {{2}}.
Si necesita una nueva hora, escriba o llame al policlínico.
```

| Variable | Dato | Ejemplo |
|---|---|---|
| {{1}} | fecha larga (minúscula) | martes 24 de noviembre de 2026 |
| {{2}} | hora | 9:20 |

### 6. `hermandad_anulacion_policlinico` (cuando el policlínico anula)

```
Hola, {{1}}. Su hora del {{2}} a las {{3}} fue anulada por el policlínico. Escriba o llame al policlínico para darle una nueva hora.
```

| Variable | Dato | Ejemplo |
|---|---|---|
| {{1}} | nombre de pila | Rosa |
| {{2}} | fecha larga (minúscula) | martes 24 de noviembre de 2026 |
| {{3}} | hora | 9:20 |

---

## Para la demo

- Con el número de prueba de Meta solo llegan mensajes a los celulares agregados en **WhatsApp → Configuración de la API → Para**
  (máximo 5). El paciente de la demo debe tener uno de esos números en su ficha.
- Las respuestas con botones llegan al backend por el webhook `https://<dominio>/webhooks/whatsapp`, que necesita una URL
  pública con HTTPS (servidor en la nube o un túnel).
- Variantes para el adulto responsable (DI-25): pendientes. Para la demo usa pacientes adultos.
