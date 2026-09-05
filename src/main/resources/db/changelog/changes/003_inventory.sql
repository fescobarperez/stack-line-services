--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Inventario. Existencias por sucursal/lote + kardex (bitácora inmutable).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:003-product_stock
--comment: Existencias por sucursal y lote. El stock NO es global; vive aquí (producto x sucursal).
CREATE TABLE product_stock (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies (id),
    product_id  BIGINT        NOT NULL REFERENCES products (id),
    branch_id   BIGINT        NOT NULL REFERENCES branches (id),
    quantity    NUMERIC(14,3) NOT NULL DEFAULT 0,
    batch       VARCHAR(40)   NOT NULL DEFAULT '',
    expiry      DATE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_stock UNIQUE (company_id, product_id, branch_id, batch)
);
CREATE INDEX ix_product_stock_product ON product_stock (product_id);
CREATE INDEX ix_product_stock_branch ON product_stock (branch_id);
--rollback DROP TABLE product_stock;

--changeset erp_maya:003-stock_movements
--comment: Kardex. Registro inmutable de cada movimiento (venta/recepción/traslado/ajuste) con signo.
CREATE TABLE stock_movements (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    product_id     BIGINT        NOT NULL REFERENCES products (id),
    branch_id      BIGINT        NOT NULL REFERENCES branches (id),
    user_id        BIGINT        REFERENCES users (id),
    movement_type  VARCHAR(20)   NOT NULL,
    quantity       NUMERIC(14,3) NOT NULL,
    ref_type       VARCHAR(20),
    ref_id         VARCHAR(40),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_stock_movements_company ON stock_movements (company_id);
CREATE INDEX ix_stock_movements_product ON stock_movements (product_id);
CREATE INDEX ix_stock_movements_branch ON stock_movements (branch_id);
CREATE INDEX ix_stock_movements_ref ON stock_movements (ref_type, ref_id);
--rollback DROP TABLE stock_movements;
