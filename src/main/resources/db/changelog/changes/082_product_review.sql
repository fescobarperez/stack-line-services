--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Productos propuestos por el asistente de WhatsApp.
--
-- Cuando el cliente pide algo que no está en el catálogo pero sí en las
-- líneas que vende la empresa, el asistente crea el producto SIN precio y
-- pendiente de revisión. No aparece en el POS ni en las búsquedas hasta que
-- un asesor lo aprueba (le pone precio) o lo descarta.
--
-- origin         manual · assistant
-- review_status  NULL (producto normal) · pending · discarded
-- review_notes   lo que el asistente sabe del pedido (ficha de diseño)
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:082-product-review
ALTER TABLE products ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'manual';
ALTER TABLE products ADD COLUMN review_status VARCHAR(20);
ALTER TABLE products ADD COLUMN review_notes TEXT;
ALTER TABLE products ADD CONSTRAINT ck_products_origin CHECK (origin IN ('manual', 'assistant'));
ALTER TABLE products ADD CONSTRAINT ck_products_review_status
    CHECK (review_status IS NULL OR review_status IN ('pending', 'discarded'));
CREATE INDEX ix_products_review ON products (company_id, review_status) WHERE review_status IS NOT NULL;
--rollback DROP INDEX ix_products_review;
--rollback ALTER TABLE products DROP CONSTRAINT ck_products_review_status;
--rollback ALTER TABLE products DROP CONSTRAINT ck_products_origin;
--rollback ALTER TABLE products DROP COLUMN review_notes;
--rollback ALTER TABLE products DROP COLUMN review_status;
--rollback ALTER TABLE products DROP COLUMN origin;
