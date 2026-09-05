--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Compras. Orden de compra y su detalle (soporta recepción parcial).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:005-purchase_orders
--comment: Encabezado de orden de compra. status permite recepciones parciales.
CREATE TABLE purchase_orders (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    doc_number   VARCHAR(40)   NOT NULL,
    supplier_id  BIGINT        REFERENCES suppliers (id),
    branch_id    BIGINT        REFERENCES branches (id),
    order_date   DATE,
    total        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status       VARCHAR(20)   NOT NULL DEFAULT 'pending',
    notes        TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_orders_doc UNIQUE (company_id, doc_number)
);
CREATE INDEX ix_purchase_orders_company ON purchase_orders (company_id);
CREATE INDEX ix_purchase_orders_supplier ON purchase_orders (supplier_id);
CREATE INDEX ix_purchase_orders_branch ON purchase_orders (branch_id);
--rollback DROP TABLE purchase_orders;

--changeset erp_maya:005-purchase_order_items
--comment: Detalle de OC. qty_ordered vs qty_received habilita recepción parcial y recálculo de costo.
CREATE TABLE purchase_order_items (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES companies (id),
    purchase_order_id  BIGINT        NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id         BIGINT        REFERENCES products (id),
    qty_ordered        NUMERIC(14,3) NOT NULL,
    qty_received       NUMERIC(14,3) NOT NULL DEFAULT 0,
    unit_cost          NUMERIC(14,4) NOT NULL
);
CREATE INDEX ix_po_items_order ON purchase_order_items (purchase_order_id);
CREATE INDEX ix_po_items_product ON purchase_order_items (product_id);
--rollback DROP TABLE purchase_order_items;
