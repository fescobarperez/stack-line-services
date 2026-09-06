--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Unidad de compra distinta de la unidad de existencia.
--
-- El caso: se compra un paquete de 100 tornillos por Q25 y se gastan 36.
-- Hasta ahora la cantidad se interpretaba siempre en la unidad del producto,
-- así que o registrabas 1 paquete —y consumías "0.36 paquetes", que en una
-- bodega no significa nada— o dividías a mano al capturar la orden.
--
-- Se resuelve con dos campos en el producto y no conectando
-- `uom_conversions`: esa tabla y `units_of_measure` existen desde la 015 con
-- su CRUD, pero nada consume el factor. Darles el trabajo implicaría FKs
-- nuevas en productos, resolución de conversiones en cada recepción y migrar
-- el texto libre de `products.unit` —hoy conviven 'unidad' y 'Unidad'—. Es
-- mucha obra por una flexibilidad que el caso no pide: un tornillo se compra
-- por paquete y se gasta por unidad, y eso no cambia por proveedor.
--
-- Con factor 1, que es el default, nada de lo existente cambia.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:049-product-purchase-unit
--comment: Cómo se compra el artículo, cuando no coincide con cómo se almacena.
ALTER TABLE products ADD COLUMN purchase_unit   VARCHAR(40);
ALTER TABLE products ADD COLUMN purchase_factor NUMERIC(18,6) NOT NULL DEFAULT 1;
ALTER TABLE products ADD CONSTRAINT ck_products_purchase_factor
    CHECK (purchase_factor > 0);
COMMENT ON COLUMN products.purchase_factor IS
    'Cuántas unidades de existencia trae una unidad de compra. Paquete de 100 '
    'tornillos = 100. La recepción multiplica la cantidad y divide el costo.';
--rollback ALTER TABLE products DROP CONSTRAINT ck_products_purchase_factor; ALTER TABLE products DROP COLUMN purchase_factor; ALTER TABLE products DROP COLUMN purchase_unit;

--changeset erp_maya:049-avg-cost-comment
--comment: Documenta qué es avg_cost, ahora que de verdad lo es.
-- Antes de esta etapa la recepción hacía setAvgCost(unitCost): era el ÚLTIMO
-- costo, sobrescrito en cada compra, no un promedio. Con eso, comprar 100
-- tornillos a Q0.25 y luego 100 a Q0.30 dejaba los 164 restantes valuados a
-- Q0.30 y le cargaba de más a cada proyecto que los consumiera.
COMMENT ON COLUMN products.avg_cost IS
    'Costo promedio móvil ponderado, recalculado en cada recepción sobre la '
    'existencia previa. Es el valor al que se descarga el consumo.';
--rollback SELECT 1;
