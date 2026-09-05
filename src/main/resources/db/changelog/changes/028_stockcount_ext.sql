--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Extensión de toma física: responsable + categoría de la sesión, y notas
-- por renglón. Estados en vocabulario del UI (in_progress/review/completed).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:028-stockcount-ext
--comment: responsable/categoría de la sesión y line_notes en renglones.
ALTER TABLE stock_counts ADD COLUMN responsible    VARCHAR(120);
ALTER TABLE stock_counts ADD COLUMN category       VARCHAR(60);
ALTER TABLE stock_counts ADD COLUMN category_label VARCHAR(120);
ALTER TABLE stock_count_items ADD COLUMN line_notes VARCHAR(300);
--rollback ALTER TABLE stock_count_items DROP COLUMN line_notes; ALTER TABLE stock_counts DROP COLUMN responsible; ALTER TABLE stock_counts DROP COLUMN category; ALTER TABLE stock_counts DROP COLUMN category_label;
