--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Catálogo · categorías anidadas y precios históricos por proveedor.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:057-catalog-hierarchy
--comment: Las categorías son un árbol navegacional; el producto conserva su ID al moverse.
ALTER TABLE categories ADD COLUMN parent_id BIGINT REFERENCES categories (id) ON DELETE RESTRICT;
CREATE INDEX ix_categories_parent ON categories (company_id, parent_id);
--rollback DROP INDEX ix_categories_parent;
--rollback ALTER TABLE categories DROP COLUMN parent_id;

--changeset erp_maya:057-product-supplier-prices
--comment: Historial de precios por proveedor desde el primer registro.
CREATE TABLE product_supplier_prices (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT        NOT NULL REFERENCES companies (id),
    product_supplier_id   BIGINT        NOT NULL REFERENCES product_suppliers (id) ON DELETE CASCADE,
    unit_cost             NUMERIC(16,4) NOT NULL,
    valid_from            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    valid_until           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_product_supplier_prices_cost CHECK (unit_cost > 0),
    CONSTRAINT ck_product_supplier_prices_dates CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE INDEX ix_product_supplier_prices_supplier ON product_supplier_prices (company_id, product_supplier_id, valid_from DESC);
--rollback DROP TABLE product_supplier_prices;
