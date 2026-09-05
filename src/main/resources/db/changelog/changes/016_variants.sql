--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Variantes de producto (tamaño, color, sabor…) sobre un producto base.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:016-product_variants
--comment: Variante de un producto base según un atributo (attribute_type/value). Cada variante tiene su SKU.
CREATE TABLE product_variants (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT        NOT NULL REFERENCES companies (id),
    product_id       BIGINT        NOT NULL REFERENCES products (id),
    attribute_type   VARCHAR(40),
    attribute_value  VARCHAR(80),
    sku              VARCHAR(64),
    price            NUMERIC(14,2),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_variants_sku UNIQUE (company_id, sku)
);
CREATE INDEX ix_product_variants_company ON product_variants (company_id);
CREATE INDEX ix_product_variants_product ON product_variants (product_id);
--rollback DROP TABLE product_variants;
