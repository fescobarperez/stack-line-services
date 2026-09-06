--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Imputación de compras a proyectos (capa 3).
--
-- Se imputa la FACTURA, no la orden de compra: una OC es una intención y
-- puede cancelarse; la factura es el costo real. Si el margen se calculara
-- sobre órdenes, un pedido anulado ensuciaría el resultado.
--
-- Las OC sí se registran, pero como COMPROMETIDO: dinero que ya te vas a
-- gastar y todavía no aparece en el ejecutado. Sin esa distinción el
-- sobrecosto se ve cuando ya ocurrió.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:042-purchase-project
--comment: Proyecto al que se imputa la factura de compra (costo ejecutado).
ALTER TABLE purchase_invoices ADD COLUMN project_id BIGINT REFERENCES projects (id);
CREATE INDEX ix_purchase_invoices_project ON purchase_invoices (project_id);
--rollback DROP INDEX ix_purchase_invoices_project; ALTER TABLE purchase_invoices DROP COLUMN project_id;

--changeset erp_maya:042-po-project
--comment: Proyecto de la orden de compra (costo comprometido hasta que se facture).
ALTER TABLE purchase_orders ADD COLUMN project_id BIGINT REFERENCES projects (id);
CREATE INDEX ix_purchase_orders_project ON purchase_orders (project_id);
--rollback DROP INDEX ix_purchase_orders_project; ALTER TABLE purchase_orders DROP COLUMN project_id;
