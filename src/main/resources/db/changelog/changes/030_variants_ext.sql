--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Extensión de variantes de producto: costo, existencia y mínimo por variante.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:030-variants-ext
--comment: cost, stock, min_stock por variante.
ALTER TABLE product_variants ADD COLUMN cost      NUMERIC(14,2) DEFAULT 0;
ALTER TABLE product_variants ADD COLUMN stock     INTEGER       DEFAULT 0;
ALTER TABLE product_variants ADD COLUMN min_stock INTEGER       DEFAULT 0;
--rollback ALTER TABLE product_variants DROP COLUMN cost; ALTER TABLE product_variants DROP COLUMN stock; ALTER TABLE product_variants DROP COLUMN min_stock;
