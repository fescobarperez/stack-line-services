# Despliegue de Stackline

Tres contenedores: Postgres, el backend Micronaut y Caddy, que sirve el
front y hace de proxy de `/api`. Como el navegador ve un solo origen, no hay
CORS que configurar.

Solo Caddy publica puertos. La base de datos y el backend viven en la red
interna de Docker y **no son alcanzables desde internet**.

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

## 3. Los repos, como hermanos

El compose espera esta disposición:

```bash
mkdir -p ~/stackline && cd ~/stackline
git clone https://github.com/fescobarperez/stack-line-services.git
git clone https://github.com/fescobarperez/stack-line-ui.git
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

## Publicar cambios

No hay despliegue automático al hacer push: `main` puede acumular commits y
tú decides cuándo publicarlos.

### Desde tu máquina

```bash
ssh -i ~/.ssh/stackline.pem ubuntu@3.142.8.206 './stackline/stack-line-services/deploy/deploy.sh'
```

El script trae ambos repositorios y **reconstruye solo lo que cambió**,
comparando el commit antes y después del pull. Un cambio en el README o en
el compose no recompila el jar, que son ~8 minutos en esta instancia.

Para forzar: `deploy.sh backend`, `deploy.sh web` o `deploy.sh all`.

Termina esperando a que el front y la API respondan; si no lo hacen en tres
minutos, imprime los logs del backend y sale con error.

### Desde GitHub

Actions → **Deploy** → *Run workflow*, eligiendo qué reconstruir. Hace lo
mismo por SSH. Requiere cuatro secretos en Settings → Secrets → Actions:

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
