# Despliegue en la nube (AWS Lightsail)

La especificación fija el despliegue con **Docker Compose** (`mysql` + `backend` + `frontend` con Nginx) y pide HTTPS
delante de Nginx en producción. En la nube se usa exactamente eso, en un servidor Linux de AWS, más **Caddy** como
proxy HTTPS (certificado Let's Encrypt automático). No hay cambios de código ni de esquema.

```
Internet ──HTTPS 443──▶ Caddy ──▶ Nginx (frontend: React + proxy /api y /webhooks) ──▶ Backend Spring Boot ──▶ MySQL 8
                                                                                              │
                                                                         WhatsApp Cloud API ◀─┘ (y su webhook entra por /webhooks/whatsapp)
```

| Archivo | Para qué |
|---|---|
| `docker-compose.nube.yml` | Se aplica encima de `docker-compose.yml`: agrega Caddy (80/443), deja MySQL y el frontend sin puertos públicos y limita la memoria de Java. |
| `Caddyfile` | HTTPS para `DOMINIO`. |
| `scripts/preparar-servidor.sh` | Una vez, en el servidor: instala Docker, crea 2 GB de swap y fija la zona horaria. |
| `scripts/desplegar.sh` | Desde tu PC: sube el código de `HEAD` y el `.env`, y reconstruye. Se repite en cada actualización. |

## Despliegue actual (entrega del 23-11-2026): AWS Academy Learner Lab

El equipo no tiene presupuesto para hosting, así que la entrega usa el **AWS Academy Learner Lab** del curso
(US$50 de crédito, sin tarjeta). Es el mismo diseño descrito abajo, con EC2 en lugar de Lightsail:

| Recurso | Valor |
|---|---|
| Región | us-east-1 (Norte de Virginia; el Learner Lab solo permite us-east-1 y us-west-2) |
| Instancia EC2 | `hermandad-agenda`, **t3.small** (2 vCPU, 2 GB) + 2 GB de swap, Ubuntu 24.04 LTS, disco gp3 de 20 GB |
| Grupo de seguridad | `launch-wizard-1`: SSH 22, HTTP 80, HTTPS 443 |
| IP elástica | `100.59.97.56` |
| URL | **https://100-59-97-56.sslip.io** (personal: `/ingreso`) |
| Llave SSH | `labsuser.pem` del Learner Lab (AWS Details → Download PEM), usuario `ubuntu` |
| Variables | `.env.nube` (fuera del repositorio): perfil `demo`, contraseñas de base de datos propias de la nube |

**Disponibilidad:** el Learner Lab apaga la instancia al terminar cada sesión (4 h). Antes de una revisión o de la
defensa: **Start Lab** y esperar ~3 minutos; los contenedores tienen `restart: unless-stopped` y se levantan solos.
Los datos persisten en el disco (volúmenes de Docker), así que no se pierden al apagar.

**Costo aproximado:** t3.small US$0,021/h encendida; IP elástica + disco ~US$0,17/día aunque esté apagada.

**Datos y Ley 21.719:** la base y los certificados quedan en el disco de la instancia, en EE. UU. (us-east-1).
Es la transferencia internacional ya identificada como riesgo en la factibilidad; para producción corresponde
revisar el contrato del proveedor, cifrar el disco y preferir una región en Chile si el proveedor la ofrece.

## 1. Crear el servidor (consola de AWS)

1. lightsail.aws.amazon.com → **Crear instancia**.
2. Región: **São Paulo (sa-east-1)** (la más cercana a Chile) o Virginia (us-east-1), que es más barata.
3. Plataforma **Linux/Unix** → solo SO → **Ubuntu 24.04 LTS**.
4. Plan: **2 GB de RAM** (Java + MySQL no caben en menos).
5. Nombre: `hermandad-agenda` → **Crear instancia**.
6. En la instancia → pestaña **Redes**:
   - **Crear IP estática** y asociarla (si no, la IP cambia al reiniciar).
   - En el firewall IPv4, **agregar regla HTTPS (443)**. SSH (22) y HTTP (80) vienen abiertos.
7. **Cuenta → Claves SSH** → descargar la clave predeterminada de la región (`LightsailDefaultKey-….pem`).
   Guárdala **fuera del repositorio**, por ejemplo en `C:\Users\<tú>\.ssh\`.

## 2. Dominio con HTTPS

Para la entrega basta un dominio gratuito que apunta a la IP: con la IP `3.91.20.15` el dominio es
**`3-91-20-15.sslip.io`**. Si más adelante la fundación tiene dominio propio, se cambia `DOMINIO` y se redespliega.

## 3. Variables de la nube

Copia `.env` como **`.env.nube`** (está en `.gitignore`) y ajusta:

```
DOMINIO=3-91-20-15.sslip.io
SPRING_PROFILES_ACTIVE=demo        # datos de demostración para la entrega
MENSAJERIA_PROVEEDOR=whatsapp
MYSQL_ROOT_PASSWORD=<una nueva, larga>
DB_CONTRASENA=<una nueva, larga>
```

Las contraseñas de la nube deben ser distintas de las locales. Nunca subas `.env.nube` al repositorio.

## 4. Preparar y desplegar (Git Bash, desde la raíz del proyecto)

```bash
LLAVE=~/.ssh/LightsailDefaultKey-sa-east-1.pem
IP=3.91.20.15

# una sola vez
scp -i $LLAVE scripts/preparar-servidor.sh ubuntu@$IP:
ssh -i $LLAVE ubuntu@$IP 'bash preparar-servidor.sh'

# cada vez que haya cambios
scripts/desplegar.sh ubuntu@$IP $LLAVE
```

El primer despliegue compila el backend y el frontend en el servidor (5 a 10 minutos). Al terminar:
**https://DOMINIO** (portada) y **https://DOMINIO/ingreso** (personal).

## 5. Webhook de WhatsApp

developers.facebook.com → app → **WhatsApp → Configuración** → Webhook:

- URL de devolución de llamada: `https://DOMINIO/webhooks/whatsapp`
- Token de verificación: el valor de `WHATSAPP_VERIFY_TOKEN` del `.env.nube`
- **Verificar y guardar** → en «Campos del webhook», suscribirse a **messages**.

## Operación

```bash
ssh -i $LLAVE ubuntu@$IP
cd hermandad-agenda
docker compose -f docker-compose.yml -f docker-compose.nube.yml ps          # estado
docker compose -f docker-compose.yml -f docker-compose.nube.yml logs -f backend   # registros
```

Respaldo: `scripts/respaldo.sh` funciona igual en el servidor (programarlo con cron, sección «Respaldos» del README).

## Costo y límites

- Lightsail 2 GB: alrededor de **US$12 al mes**, con IP incluida. AWS suele ofrecer los primeros meses gratis en planes
  nuevos; revisa la oferta vigente al crear la instancia. Apaga o borra la instancia cuando ya no se use.
- El token temporal de WhatsApp dura 24 horas: para la demo hay que generar un token permanente (usuario del sistema)
  y actualizar `WHATSAPP_TOKEN` en `.env.nube` antes de redesplegar.
