# San Miguel · catálogo de materiales

**SOP:** `san-miguel-catalog`
**Versión:** `v1`
**Propósito:** extraer productos del catálogo público para generar un lote de pre-autorización.

## Reglas obligatorias

1. Recorrer todas las categorías solicitadas por el alcance de la ejecución.
2. Para cada categoría, extraer la página actual y seguir el enlace de paginación siguiente hasta agotarlo.
3. No considerar completa una categoría por haber leído únicamente la primera página.
4. Registrar cada URL descubierta y visitada, incluyendo páginas `/page/2`, `/page/3`, etc.
5. No repetir una URL ya visitada.
6. Si una página falla, conservar los productos ya encontrados, marcar la ejecución como incompleta y reportar la URL fallida.
7. Conservar la URL de categoría como `source_listing_url`.
8. Usar la ficha individual como `product_url` cuando la página exponga un enlace de producto.
9. No crear ni actualizar productos durante el crawling. Solo producir registros normalizados para pre-autorización.

## Campos mínimos

- nombre original del producto;
- precio y moneda;
- categoría y subcategoría originales;
- URL de origen;
- unidad o presentación cuando pueda determinarse.

## Normalización

- Mantener el texto original sin sobrescribirlo.
- Interpretar dimensiones como atributos técnicos, no como parte descartable del nombre.
- No asumir que dos productos con nombres parecidos son el mismo si cambian dimensiones, presentación o unidad.
- Si falta el precio, conservar la fila y marcarla para `REVIEW`.

## Resultado de calidad

La ejecución debe informar páginas descubiertas, visitadas y fallidas, productos únicos extraídos y una clasificación `complete`, `incomplete`, `partial` o `failed`.
