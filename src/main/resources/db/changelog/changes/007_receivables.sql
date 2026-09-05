--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Cuentas por cobrar. Abonos de clientes (opcionalmente contra una factura).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:007-payments
--comment: Abonos de clientes. sale_id es opcional (pago a factura específica o abono general a cuenta).
CREATE TABLE payments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    client_id     BIGINT        NOT NULL REFERENCES clients (id),
    sale_id       BIGINT        REFERENCES sales (id),
    amount        NUMERIC(14,2) NOT NULL,
    payment_date  DATE          NOT NULL DEFAULT CURRENT_DATE,
    method        VARCHAR(30),
    reference     VARCHAR(80),
    notes         TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_company ON payments (company_id);
CREATE INDEX ix_payments_client ON payments (client_id);
CREATE INDEX ix_payments_sale ON payments (sale_id);
--rollback DROP TABLE payments;
