--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Precio por medida especial en la línea de cotización.
--
-- quote_items.size_pricing   NULL = precio del catálogo o el que puso el
--                            asesor; 'calculated' = el ERP lo calculó por
--                            volumen + parte fija; 'review' = la medida se sale
--                            de la tolerancia y la línea espera que el asesor
--                            le ponga precio.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:078-quote-item-size-pricing
ALTER TABLE quote_items ADD COLUMN size_pricing VARCHAR(20);
--rollback ALTER TABLE quote_items DROP COLUMN size_pricing;
