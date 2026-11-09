# Stackline deployment

The production stack runs four application containers: the Micronaut backend,
the Python crawler, the sales agent, and Caddy for the web application and
reverse proxy. PostgreSQL is available only through the optional `local-db`
profile because deployed environments use RDS.

Only Caddy publishes host ports. The backend, crawler, agent, and database stay
inside the Docker network.

---

## 1. La máquina

Con 2 vCPU y 4 GB va sobrado. Con 2 GB también corre, bajando `JAVA_OPTS`
a `-XX:MaxRAMPercentage=50`.

| Proveedor | Tipo sugerido | Dónde se abre el puerto 80 |
|---|---|---|
| AWS EC2 | `t3.small` / `t4g.small` | Security group → regla de entrada |
| GCP Compute Engine | `e2-small` | Reglas de firewall de VPC |
| Azure | `B2s` | Network Security Group |

**Reserva una IP estática** (Elastic IP en AWS, dirección externa estática en
GCP). Sin eso la IP cambia al reiniciar la instancia y el acceso se rompe.

Abre **22** (SSH, mejor restringido a tu IP) y **80**. No abras el 5432: la
base no debe verse desde fuera.

## 2. Docker

```bash
# Ubuntu 26.04, 24.04 o 22.04 (todas soportadas por Docker)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && exec newgrp docker
```

## 3. Sibling repositories

Docker Compose expects this directory layout. `deploy.sh` clones the public
crawler repository automatically if it is missing; the commands below show the
complete initial setup.

```bash
mkdir -p ~/stackline && cd ~/stackline
git clone https://github.com/fescobarperez/stack-line-services.git
git clone https://github.com/fescobarperez/stack-line-ui.git
git clone https://github.com/fescobarperez/agents-services.git
git clone --branch feat/initial-crawling-runtime https://github.com/bragonya/sites-crawler.git
```

## 4. Configuración

```bash
cd ~/stackline/stack-line-services/deploy
cp .env.example .env
openssl rand -base64 24   # → DB_PASSWORD
openssl rand -base64 48   # → JWT_SECRET
openssl rand -base64 18   # → SEED_ADMIN_PASSWORD
nano .env
```

`SITE_ADDRESS=:80` deja el sitio en HTTP plano.

`SEED_COMPANY_CODE` es lo que se escribe en la pantalla de login junto al
correo. El usuario inicial se crea en el primer arranque y no se vuelve a
tocar; cambiar la variable después no lo modifica.

## 5. Arrancar

```bash
docker compose up -d --build     # la primera vez tarda: compila el jar
docker compose logs -f backend   # Liquibase aplica las migraciones al subir
```

Entra en `http://TU-IP` con el código de empresa, correo y contraseña
del `.env`.

---

## HTTP plano: qué implica

La contraseña y el token de sesión viajan **sin cifrar**. Cualquiera en la
ruta entre el navegador y el servidor puede leerlos, y un token robado sirve
8 horas. Está bien para enseñar el sistema con datos de prueba; no metas
datos reales de clientes ni de facturación mientras siga así.

Cuando quieras cerrarlo, sin comprar dominio, son dos líneas en `.env`:

```
SITE_ADDRESS=203-0-113-10.sslip.io   # tu IP con guiones
ACME_EMAIL=tu@correo.com
```

`sslip.io` resuelve al valor que lleva en el nombre, así que Let's Encrypt
puede validarlo y Caddy saca el certificado solo. Después: `docker compose up -d`.

---

## Python crawler

The `crawling` container is built from `~/stackline/sites-crawler`. It shares
`crawlingdata` with the backend but does not publish port 8090 on the host. The
backend reaches it at `http://crawling:8090`.

Before the first rollout, set these values in `deploy/.env`:

```dotenv
AI_MODE=real
AI_CHAT_MODEL=gpt-6-luna
AI_CHAT_BASE_URL=https://api.openai.com/v1/chat/completions
OPENAI_API_KEY=<set-locally-on-the-server>
CRAWLING_SERVICE_URL=http://crawling:8090
```

Do not commit the real API key. The deployment preflight fails before changing
containers when the key is missing. The crawler health check confirms that the
HTTP process is running; a release smoke test must still execute a real model
request to verify that the configured provider accepts `AI_CHAT_MODEL`.

## Agente de ventas (agents-services)

Cuarto contenedor, `agents`. No publica puertos: el backend le habla por la
red interna y Caddy solo le reenvía `/webhooks/whatsapp`.

**Una sola vez**, antes del primer arranque, crea el esquema del agente:

```bash
# Esquemas por ambiente en la misma base (Liquibase no los crea):
#   dev  → stackline_dev  y agents_dev
#   prod → stackline_prod y agents_prod
docker run --rm -it postgres:16-alpine \
  psql "host=<endpoint-rds> dbname=erp_maya user=erp_maya" \
  -c "CREATE SCHEMA IF NOT EXISTS agents_dev;"
```

Completa en `.env` la sección «Asistente comercial» (ver `.env.example`) y:

```bash
cd ~/stackline/stack-line-services/deploy
docker compose up -d --build agents backend web
docker compose logs -f agents      # Liquibase crea sus tablas en agents_<APP_ENV>
```

Memoria: en una máquina de 2 GB, `AGENTS_MEM_LIMIT=768m` y
`JAVA_OPTS=-XX:MaxRAMPercentage=50` para el backend. Compilar el agente
(Kotlin) pide ~1 GB extra durante unos minutos; si la máquina no tiene swap,
agrega 2 GB antes del primer build.

WhatsApp: Meta exige HTTPS, así que el webhook solo funciona con
`SITE_ADDRESS` en HTTPS (p. ej. `<ip-con-guiones>.sslip.io`). La URL que se
registra en Meta es `https://<SITE_ADDRESS>/webhooks/whatsapp`.

## Publicar cambios

No hay despliegue automático al hacer push: `main` puede acumular commits y
tú decides cuándo publicarlos.

### Desde tu máquina

```bash
ssh -i ~/.ssh/stackline.pem ubuntu@3.142.8.206 './stackline/stack-line-services/deploy/deploy.sh'
```

The script updates all four repositories and rebuilds only images whose source
changed. Use `deploy.sh backend`, `deploy.sh web`, `deploy.sh agents`, or
`deploy.sh crawling` for a targeted rollout. Use `deploy.sh all` for the first
crawler rollout so every dependency is started from a known revision.

Verification requires the web application, authenticated API boundary, sales
agent route, and crawler `/healthz` endpoint to respond.

### Desde GitHub

The workflow fetches the deployment orchestrator from `origin/main` and executes
that version over SSH. The script then records the currently deployed commits,
fast-forwards the selected repositories, and rebuilds the requested images. It
requires these repository secrets:

| Secreto | Valor |
|---|---|
| `SSH_PRIVATE_KEY` | contenido completo de `stackline.pem` |
| `SSH_HOST` | la IP elástica del servidor |
| `SSH_USER` | `ubuntu` |
| `SSH_KNOWN_HOSTS` | salida de `ssh-keyscan -t ed25519 <IP>` |

`SSH_KNOWN_HOSTS` evita aceptar a ciegas cualquier servidor que responda: sin
él, un intermediario podría suplantarlo y quedarse con la clave privada.

Para exigir aprobación antes de cada despliegue: Settings → Environments →
`produccion` → **Required reviewers**. El flujo queda en espera hasta que
alguien lo apruebe.

### Las migraciones no llevan paso aparte

Liquibase corre al arrancar el backend, así que se aplican solas. El
corolario es que una migración con error impide el arranque — y el script lo
detecta y te muestra el log.

## Operación

```bash
docker compose ps                      # estado
docker compose logs -f backend
docker compose pull && docker compose up -d --build    # actualizar

# Respaldo (la base NO está expuesta: se entra por el contenedor)
docker compose exec -T db pg_dump -U erp_maya erp_maya | gzip > backup-$(date +%F).sql.gz

# Restaurar
gunzip -c backup-2026-09-06.sql.gz | docker compose exec -T db psql -U erp_maya erp_maya

# Conectarse a la base desde tu máquina, sin abrirla al mundo
ssh -L 5433:localhost:5432 usuario@TU-IP    # y luego apuntar a localhost:5433
```

El volumen `pgdata` sobrevive a `docker compose down`. Para borrarlo de
verdad hace falta `down -v`, que **elimina la base entera**.

## Llevar tus datos actuales

Si quieres arrancar con lo que tienes en local en vez de una instalación
limpia, en tu máquina:

```bash
docker exec maya-postgres pg_dump -U erp_maya erp_maya | gzip > local.sql.gz
scp local.sql.gz usuario@TU-IP:~
```

Y en el servidor, **antes** del primer `up` (o con `SEED_ENABLED=false` para
que no cree una empresa que ya viene en el volcado):

```bash
docker compose up -d db
gunzip -c ~/local.sql.gz | docker compose exec -T db psql -U erp_maya erp_maya
docker compose up -d
```
