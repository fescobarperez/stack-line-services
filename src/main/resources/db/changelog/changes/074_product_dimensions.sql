--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Medidas de producto y medidas pedidas en la línea de cotización.
--
-- products.height/width/depth   medida estándar del catálogo (opcional).
-- products.dimension_unit       unidad de esas medidas (m por defecto).
-- quote_items.height/width/depth        la medida que pide el cliente; al
--                                       agregar el producto nace igual a la
--                                       del catálogo.
-- quote_items.base_height/width/depth   copia de la medida del catálogo al
--                                       momento de cotizar: si el producto
--                                       cambia, la cotización sigue sabiendo
--                                       contra qué se comparó. Si la pedida
--                                       difiere de la base, es "medida especial".
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:074-product-dimensions
ALTER TABLE products ADD COLUMN height NUMERIC(10,3);
ALTER TABLE products ADD COLUMN width NUMERIC(10,3);
ALTER TABLE products ADD COLUMN depth NUMERIC(10,3);
ALTER TABLE products ADD COLUMN dimension_unit VARCHAR(10);
ALTER TABLE quote_items ADD COLUMN height NUMERIC(10,3);
ALTER TABLE quote_items ADD COLUMN width NUMERIC(10,3);
ALTER TABLE quote_items ADD COLUMN depth NUMERIC(10,3);
ALTER TABLE quote_items ADD COLUMN base_height NUMERIC(10,3);
ALTER TABLE quote_items ADD COLUMN base_width NUMERIC(10,3);
ALTER TABLE quote_items ADD COLUMN base_depth NUMERIC(10,3);
--rollback ALTER TABLE quote_items DROP COLUMN base_depth; ALTER TABLE quote_items DROP COLUMN base_width; ALTER TABLE quote_items DROP COLUMN base_height; ALTER TABLE quote_items DROP COLUMN depth; ALTER TABLE quote_items DROP COLUMN width; ALTER TABLE quote_items DROP COLUMN height; ALTER TABLE products DROP COLUMN dimension_unit; ALTER TABLE products DROP COLUMN depth; ALTER TABLE products DROP COLUMN width; ALTER TABLE products DROP COLUMN height;
