--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Proveedores por producto · cada relación conserva su costo unitario.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:056-product-suppliers
--comment: Un producto puede comprarse a varios proveedores con costos distintos.
CREATE TABLE product_suppliers (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    product_id    BIGINT        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    supplier_id   BIGINT        NOT NULL REFERENCES suppliers (id),
    unit_cost     NUMERIC(16,4) NOT NULL,
    preferred     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_suppliers_product_supplier UNIQUE (company_id, product_id, supplier_id),
    CONSTRAINT ck_product_suppliers_cost CHECK (unit_cost > 0)
);
CREATE INDEX ix_product_suppliers_product ON product_suppliers (company_id, product_id, preferred);
CREATE INDEX ix_product_suppliers_supplier ON product_suppliers (company_id, supplier_id);
--rollback DROP TABLE product_suppliers;
