# Stackline · Servicios

Backend del ERP **Stackline**, para comercio y servicios en Guatemala.
Micronaut sobre PostgreSQL, multiempresa.

El front vive en un repositorio aparte: `stack-line-ui`. Los dos se clonan
como hermanos; el despliegue lo orquesta `deploy/` de este repositorio.

## Lo que hay que saber antes de tocar nada

**Todo el código, los comentarios y los mensajes de commit van en español.**
Los identificadores en inglés, como ya está.

**Es un ERP contable en producción incipiente.** Un error aquí no rompe una
pantalla: descuadra un libro mayor, duplica un ingreso o le cobra de más a
un cliente. Ante la duda, fallar ruidosamente es mejor que continuar con un
dato dudoso.

## Arranque

```bash
cp .env.example .env      # y poner DB_PASSWORD
./gradlew run             # o el botón de IntelliJ, da igual
```

`Application.main` carga el `.env` del directorio de trabajo y activa el
ambiente `dev`, así que no hay variables que exportar. Lo que ya venga del
entorno gana sobre el archivo.

Hay **un solo ambiente: `dev`**, contra una base PostgreSQL en RDS que
comparten tu máquina y el servidor desplegado. No existe base local. Lo que
borres probando se ve en el servidor.

## Arquitectura

- **Micronaut 5**, Java 25, Gradle con Kotlin DSL
- **PostgreSQL** con **Liquibase**; Hibernate no genera esquema (`hbm2ddl=none`)
- **Multiempresa** por `company_id` en cada tabla, resuelto por `TenantContext`
  desde el JWT. Toda consulta filtra por empresa — omitirlo filtra datos entre
  clientes.
- **JWT bearer**. `/api/auth/login` es anónimo; el resto exige token.

Un paquete por dominio bajo `com.erp_maya`: `pos`, `catalog`, `inventory`,
`project`, `receivable`, `payable`, `purchasing`, `accounting`,
`authorization`, `fel`, `bank`, `payroll`, `quote`, `sequence`, `settings`…
Cada uno con `domain` / `repository` / `dto` / `service` / `controller`.

## Reglas que cuestan caro romper

**Una migración aplicada no se edita.** Liquibase guarda su checksum; cambiarla
rompe el arranque en toda base que ya la tenga. Se corrige con una migración
nueva. Van en `src/main/resources/db/changelog/changes/NNN_nombre.sql`, en
formato SQL de Liquibase, con `--rollback` y declaradas en el master.

**Las partidas contables nacen solo en `PostingService`.** Resuelve las cuentas
del mapeo de `company_settings`, valida que débitos y créditos cuadren —si no,
lanza excepción en vez de guardar—, resuelve el período y sabe reversar. Nadie
más arma una partida a mano.

**El IVA va incluido en el precio** (Guatemala, 12% configurable). El ingreso es
el total *menos* el impuesto. Registrar el total como ingreso infla las ventas
y paga ISR de más.

**Los importes con signo se suman por `signed_total`**, no por `total`. Una nota
de crédito resta sola; sumar `total` la contaría como venta.

**`IllegalStateException` se traduce a HTTP 409** (ver `IllegalStateHandler`).
Es la vía para reglas de negocio violadas, con un mensaje que diga qué hacer.

**Cuidado con `@Transactional` y las llamadas internas.** Una llamada de un
método a otro de la misma clase no pasa por el proxy de Micronaut y pierde la
transacción — de ahí salen los `LazyInitializationException`.

**Nunca un secreto en el repositorio.** Es público. La contraseña de la base y
el secreto JWT llegan por variable de entorno: del `.env` local o del `.env`
del servidor. Revisar el diff antes de commitear; el IDE agrega archivos solos.

## Dominio

Guatemala: facturación electrónica **FEL/SAT** con tipos de DTE (`FACT`, `NCRE`,
`NDEB`…), IVA incluido, Régimen General por lo devengado.

Piezas propias del negocio, por si no son obvias:

- **Turnos de caja**: una caja abierta se asocia a un usuario y hay que cerrarla
  con su arqueo antes de operar al día siguiente.
- **Autorizaciones**: motor genérico y reutilizable con niveles jerárquicos, por
  PIN o por bandeja. Se usa en descuentos libres del POS y en sobrecosto y
  sobrefacturación de proyectos.
- **Proyectos**: siguen la rentabilidad de un trabajo. Contratado congelado al
  convertir la cotización, ejecutado, comprometido, facturado y cobrado.
- **Materia prima**: `products.item_type` decide si algo se vende en POS, se
  consume en un proyecto o es un servicio. La categoría es solo agrupación.
- **Unidad de compra**: `purchase_factor` traduce «un paquete de 100 tornillos»
  a 100 unidades en bodega, dividiendo el costo.

## Verificar antes de dar algo por hecho

`./gradlew compileJava` **no** basta. Muchos errores de esta base solo aparecen
al ejecutar: proxies de transacción, columnas generadas, consultas nativas que
devuelven una forma distinta a la esperada. Levantar el servicio y pegarle con
`curl` es la única prueba que cuenta.

Al probar contra la base compartida, **limpiar lo que se creó** — y filtrar por
una marca propia, no por nombre de producto o patrones amplios.

## Despliegue

En `deploy/README.md`. Resumen: Docker Compose con el backend y Caddy en una
EC2, RDS aparte, publicación **manual** desde Actions.
