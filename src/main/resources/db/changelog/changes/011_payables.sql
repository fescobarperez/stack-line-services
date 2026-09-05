--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Cuentas por pagar (CxP). Facturas de proveedor y sus pagos.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:011-purchase_invoices
--comment: Factura de proveedor (deuda). Puede originarse de una OC. paid_amount vs amount da el saldo.
CREATE TABLE purchase_invoices (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES companies (id),
    doc_number         VARCHAR(40)   NOT NULL,
    supplier_id        BIGINT        NOT NULL REFERENCES suppliers (id),
    purchase_order_id  BIGINT        REFERENCES purchase_orders (id),
    invoice_date       DATE,
    due_date           DATE,
    amount             NUMERIC(14,2) NOT NULL DEFAULT 0,
    paid_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL DEFAULT 'open',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_invoices_doc UNIQUE (company_id, doc_number)
);
CREATE INDEX ix_purchase_invoices_company ON purchase_invoices (company_id);
CREATE INDEX ix_purchase_invoices_supplier ON purchase_invoices (supplier_id);
CREATE INDEX ix_purchase_invoices_order ON purchase_invoices (purchase_order_id);
--rollback DROP TABLE purchase_invoices;

--changeset erp_maya:011-supplier_payments
--comment: Pagos a proveedores. Opcionalmente aplicados a una factura específica (purchase_invoice_id).
CREATE TABLE supplier_payments (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id           BIGINT        NOT NULL REFERENCES companies (id),
    supplier_id          BIGINT        NOT NULL REFERENCES suppliers (id),
    purchase_invoice_id  BIGINT        REFERENCES purchase_invoices (id),
    amount               NUMERIC(14,2) NOT NULL,
    payment_date         DATE          NOT NULL DEFAULT CURRENT_DATE,
    method               VARCHAR(30),
    reference            VARCHAR(80),
    notes                TEXT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_supplier_payments_company ON supplier_payments (company_id);
CREATE INDEX ix_supplier_payments_supplier ON supplier_payments (supplier_id);
CREATE INDEX ix_supplier_payments_invoice ON supplier_payments (purchase_invoice_id);
--rollback DROP TABLE supplier_payments;
