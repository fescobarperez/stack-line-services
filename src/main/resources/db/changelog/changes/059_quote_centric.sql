--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Modelo cotización-céntrico · Fase 1.
--
-- La cotización pasa a ser la unidad comercial: sus líneas se componen de
-- materiales del proyecto, un material pertenece a lo sumo a una cotización
-- (se libera si la cotización se cancela), y el cobro se registra por
-- cotización, no por proyecto.
--
-- Ver diseño: design/cotizacion-centrica-diseno.md
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:059-material-quote
--comment: Un material del proyecto pertenece a lo sumo a una cotización. NULL = disponible en el pool.
ALTER TABLE project_materials
    ADD COLUMN quote_id BIGINT REFERENCES quotes (id) ON DELETE SET NULL;
ALTER TABLE project_materials
    ADD COLUMN quote_item_id BIGINT REFERENCES quote_items (id) ON DELETE SET NULL;
-- Exclusividad: un material tiene una sola fila con un solo quote_id, así que
-- pertenecer a dos cotizaciones a la vez es imposible por construcción. El
-- índice parcial solo acelera "materiales de la cotización X" y "disponibles".
CREATE INDEX ix_project_materials_quote
    ON project_materials (company_id, quote_id) WHERE quote_id IS NOT NULL;
--rollback DROP INDEX ix_project_materials_quote; ALTER TABLE project_materials DROP COLUMN quote_item_id; ALTER TABLE project_materials DROP COLUMN quote_id;

--changeset erp_maya:059-quote-item-line
--comment: La línea de cotización muestra al cliente UN nivel (description). source_group_id da el default de nombre de carpeta.
ALTER TABLE quote_items
    ADD COLUMN description VARCHAR(300);
ALTER TABLE quote_items
    ADD COLUMN source_group_id BIGINT REFERENCES project_material_groups (id) ON DELETE SET NULL;
--rollback ALTER TABLE quote_items DROP COLUMN source_group_id; ALTER TABLE quote_items DROP COLUMN description;

--changeset erp_maya:059-payment-quote
--comment: El cobro se registra por cotización. project_id queda como referencia de agregación.
ALTER TABLE payments
    ADD COLUMN quote_id BIGINT REFERENCES quotes (id) ON DELETE SET NULL;
CREATE INDEX ix_payments_quote ON payments (company_id, quote_id) WHERE quote_id IS NOT NULL;
--rollback DROP INDEX ix_payments_quote; ALTER TABLE payments DROP COLUMN quote_id;
