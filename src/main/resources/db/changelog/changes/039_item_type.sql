--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Clasificación de artículos.
--
-- Hasta ahora todo lo de `products` era vendible en POS. Hacen falta dos
-- clases más para el módulo de proyectos:
--
--   sellable      producto terminado: se vende en POS
--   raw_material  materia prima: se compra y se CONSUME, no se vende.
--                 Su costo se imputa al consumir, no al comprar: si se
--                 imputara la compra, el primer proyecto cargaría con el
--                 lote entero y los siguientes saldrían gratis.
--   service       mano de obra y servicios: sin existencias, se cobra por
--                 unidad de tiempo o por trabajo.
--
-- Se queda todo en `products` a propósito: kardex, costo promedio, mínimos
-- y compras ya funcionan. Una tabla aparte obligaría a duplicarlos.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:039-product-item-type
--comment: Tipo de artículo. Lo existente es vendible: no cambia nada de lo que ya opera.
ALTER TABLE products ADD COLUMN item_type VARCHAR(20) NOT NULL DEFAULT 'sellable';
ALTER TABLE products ADD CONSTRAINT ck_products_item_type
    CHECK (item_type IN ('sellable', 'raw_material', 'service'));
CREATE INDEX ix_products_item_type ON products (company_id, item_type);
--rollback DROP INDEX ix_products_item_type; ALTER TABLE products DROP CONSTRAINT ck_products_item_type; ALTER TABLE products DROP COLUMN item_type;

--changeset erp_maya:039-service-no-stock
--comment: Un servicio no lleva existencias; el mínimo de stock no aplica.
ALTER TABLE products ADD COLUMN tracks_stock BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE products SET tracks_stock = FALSE WHERE item_type = 'service';
--rollback ALTER TABLE products DROP COLUMN tracks_stock;

--changeset erp_maya:039-stock-movement-consumption
--comment: Tipo de movimiento para el consumo de materia prima en un proyecto.
--comment: Documenta el vocabulario; movement_type es VARCHAR sin restricción.
COMMENT ON COLUMN stock_movements.movement_type IS
    'sale | purchase | adjust | transfer | count | project_consumption';
--rollback COMMENT ON COLUMN stock_movements.movement_type IS NULL;
