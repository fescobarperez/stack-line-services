--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Descuentos en la venta: origen y trazabilidad.
--
-- Hasta ahora `sale_items.discount` era un número suelto sin origen, y el
-- POS mandaba siempre 0: ni el descuento manual ni las promociones llegaban
-- a la venta. Por eso `promotion_usage` está vacía y las estadísticas del
-- módulo de Promociones no salen de ventas reales.
--
-- Distinguir el origen es además el requisito del módulo de autorizaciones:
-- un descuento de promoción ya lo aprobó quien la configuró; uno de ingreso
-- libre es criterio del cajero en el momento y sí requiere autorización.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:034-sale-item-discount-source
--comment: De dónde viene el descuento de la línea y, si es promoción, cuál.
ALTER TABLE sale_items ADD COLUMN discount_source VARCHAR(10) NOT NULL DEFAULT 'none';
ALTER TABLE sale_items ADD COLUMN promotion_id    BIGINT REFERENCES promotions (id);
ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_discount_source
    CHECK (discount_source IN ('none', 'promo', 'manual'));
CREATE INDEX ix_sale_items_promotion ON sale_items (promotion_id);
--rollback ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_discount_source; ALTER TABLE sale_items DROP COLUMN promotion_id; ALTER TABLE sale_items DROP COLUMN discount_source;

--changeset erp_maya:034-sale-item-discount-backfill
--comment: Líneas existentes con descuento: origen desconocido, se marcan como manual.
UPDATE sale_items SET discount_source = 'manual' WHERE discount > 0;
--rollback UPDATE sale_items SET discount_source = 'none';

--changeset erp_maya:034-sale-discount-header
--comment: Descuento manual de cabecera (sobre el total) y el % efectivo que se autorizó.
ALTER TABLE sales ADD COLUMN discount_total   NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN discount_percent NUMERIC(6,3)  NOT NULL DEFAULT 0;
--rollback ALTER TABLE sales DROP COLUMN discount_percent; ALTER TABLE sales DROP COLUMN discount_total;

--changeset erp_maya:034-promotion-usage-fk
--comment: promotion_usage.sale_id era un BIGINT suelto, sin integridad referencial.
DELETE FROM promotion_usage WHERE sale_id IS NOT NULL
  AND sale_id NOT IN (SELECT id FROM sales);
ALTER TABLE promotion_usage
    ADD CONSTRAINT fk_promotion_usage_sale FOREIGN KEY (sale_id) REFERENCES sales (id) ON DELETE CASCADE;
CREATE INDEX ix_promotion_usage_sale ON promotion_usage (sale_id);
--rollback ALTER TABLE promotion_usage DROP CONSTRAINT fk_promotion_usage_sale; DROP INDEX ix_promotion_usage_sale;
