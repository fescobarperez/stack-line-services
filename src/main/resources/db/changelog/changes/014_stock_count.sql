--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Toma física de inventario (conteo) y su detalle con diferencias.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:014-stock_counts
--comment: Sesión de conteo físico por sucursal. Al cerrar genera ajustes de stock por las diferencias.
CREATE TABLE stock_counts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    doc_number  VARCHAR(40)  NOT NULL,
    branch_id   BIGINT       NOT NULL REFERENCES branches (id),
    count_date  DATE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_stock_counts_doc UNIQUE (company_id, doc_number)
);
CREATE INDEX ix_stock_counts_company ON stock_counts (company_id);
CREATE INDEX ix_stock_counts_branch ON stock_counts (branch_id);
--rollback DROP TABLE stock_counts;

--changeset erp_maya:014-stock_count_items
--comment: Renglón del conteo: cantidad de sistema (teórica) vs contada (física) y su diferencia.
CREATE TABLE stock_count_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT        NOT NULL REFERENCES companies (id),
    stock_count_id  BIGINT        NOT NULL REFERENCES stock_counts (id) ON DELETE CASCADE,
    product_id      BIGINT        REFERENCES products (id),
    system_qty      NUMERIC(14,3) NOT NULL DEFAULT 0,
    counted_qty     NUMERIC(14,3),
    difference      NUMERIC(14,3)
);
CREATE INDEX ix_stock_count_items_count ON stock_count_items (stock_count_id);
CREATE INDEX ix_stock_count_items_product ON stock_count_items (product_id);
--rollback DROP TABLE stock_count_items;
