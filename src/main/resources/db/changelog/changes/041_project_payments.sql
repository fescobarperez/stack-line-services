--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Cobros de proyecto (capa 5).
--
-- Se reutiliza `payments` en vez de crear una tabla aparte: un cobro de
-- proyecto ES un cobro del cliente, y separarlos duplicaría el concepto y
-- dejaría Cuentas por Cobrar sin ver ese dinero.
--
-- `sale_id` sigue siendo nullable a propósito: un adelanto se cobra antes
-- de que exista factura. Sin modalidad impuesta — adelanto, por avance o
-- al final es decisión del usuario.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:041-payment-project
--comment: A qué proyecto pertenece el cobro, si es que pertenece a alguno.
ALTER TABLE payments ADD COLUMN project_id BIGINT REFERENCES projects (id);
CREATE INDEX ix_payments_project ON payments (project_id);
--rollback DROP INDEX ix_payments_project; ALTER TABLE payments DROP COLUMN project_id;
