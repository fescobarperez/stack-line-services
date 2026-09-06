--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Custodia de la factura original.
--
-- El flujo: se factura el total, la factura queda en cuentas por cobrar, y
-- el cliente va abonando contra recibos. El documento original no se le
-- entrega hasta que salda — es la práctica con la que arrancó todo este
-- análisis.
--
-- Es un dato de logística del papel, no del documento fiscal: el DTE ya se
-- emitió y se certificó al facturar, y esto solo registra si el impreso ya
-- salió de la oficina. Por eso no toca `status` ni nada que vea SAT.
--
-- Nulo = todavía no se entrega. Es el estado de casi todo lo que existe hoy,
-- así que no hace falta rellenar nada.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:051-sale-delivery
--comment: Cuándo se entregó el impreso original al cliente, y a quién.
ALTER TABLE sales ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE sales ADD COLUMN delivered_to VARCHAR(160);
CREATE INDEX ix_sales_undelivered ON sales (company_id)
    WHERE is_credit AND delivered_at IS NULL;
COMMENT ON COLUMN sales.delivered_at IS
    'Entrega del impreso original al cliente. Nulo = retenida. No tiene efecto '
    'fiscal: el DTE se emitió al facturar.';
--rollback DROP INDEX ix_sales_undelivered; ALTER TABLE sales DROP COLUMN delivered_to; ALTER TABLE sales DROP COLUMN delivered_at;
