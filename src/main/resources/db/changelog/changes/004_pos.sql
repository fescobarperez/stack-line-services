--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Ventas / POS. Turno de caja, encabezado de venta y su detalle.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:004-cash_registers
--comment: Turno/arqueo de caja. Agrupa las ventas de una sesión para cuadrar efectivo vs tarjeta.
CREATE TABLE cash_registers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT        NOT NULL REFERENCES companies (id),
    branch_id       BIGINT        NOT NULL REFERENCES branches (id),
    user_id         BIGINT        REFERENCES users (id),
    opened_at       TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    opening_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    closing_amount  NUMERIC(14,2),
    sales_total     NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_cash      NUMERIC(14,2) NOT NULL DEFAULT 0,
    sales_card      NUMERIC(14,2) NOT NULL DEFAULT 0,
    refunds         NUMERIC(14,2) NOT NULL DEFAULT 0,
    difference      NUMERIC(14,2),
    status          VARCHAR(20)   NOT NULL DEFAULT 'open',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_cash_registers_company ON cash_registers (company_id);
CREATE INDEX ix_cash_registers_branch ON cash_registers (branch_id);
--rollback DROP TABLE cash_registers;

--changeset erp_maya:004-sales
--comment: Encabezado de factura/ticket: totales, impuestos, estado. doc_number es la referencia fiscal.
CREATE TABLE sales (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES companies (id),
    doc_number        VARCHAR(40)   NOT NULL,
    client_id         BIGINT        REFERENCES clients (id),
    branch_id         BIGINT        NOT NULL REFERENCES branches (id),
    user_id           BIGINT        REFERENCES users (id),
    cash_register_id  BIGINT        REFERENCES cash_registers (id),
    sale_date         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    payment_method    VARCHAR(30),
    subtotal          NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax               NUMERIC(14,2) NOT NULL DEFAULT 0,
    total             NUMERIC(14,2) NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL DEFAULT 'paid',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sales_doc UNIQUE (company_id, doc_number)
);
CREATE INDEX ix_sales_company ON sales (company_id);
CREATE INDEX ix_sales_branch ON sales (branch_id);
CREATE INDEX ix_sales_client ON sales (client_id);
CREATE INDEX ix_sales_register ON sales (cash_register_id);
--rollback DROP TABLE sales;

--changeset erp_maya:004-sale_items
--comment: Detalle de venta. Guarda unit_price histórico (precio al momento de vender), no el actual.
CREATE TABLE sale_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies (id),
    sale_id     BIGINT        NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    product_id  BIGINT        REFERENCES products (id),
    quantity    NUMERIC(14,3) NOT NULL,
    unit_price  NUMERIC(14,2) NOT NULL,
    discount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total  NUMERIC(14,2) NOT NULL
);
CREATE INDEX ix_sale_items_sale ON sale_items (sale_id);
CREATE INDEX ix_sale_items_product ON sale_items (product_id);
CREATE INDEX ix_sale_items_company ON sale_items (company_id);
--rollback DROP TABLE sale_items;
