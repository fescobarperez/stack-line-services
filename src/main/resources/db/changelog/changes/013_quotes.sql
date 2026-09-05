--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Cotizaciones a clientes y su detalle.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:013-quotes
--comment: Cotización. client_id opcional + datos denormalizados (client_name/nit) para prospectos no registrados.
CREATE TABLE quotes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    doc_number   VARCHAR(40)   NOT NULL,
    client_id    BIGINT        REFERENCES clients (id),
    client_name  VARCHAR(200),
    client_nit   VARCHAR(20),
    quote_date   DATE,
    valid_until  DATE,
    subtotal     NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax          NUMERIC(14,2) NOT NULL DEFAULT 0,
    total        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status       VARCHAR(20)   NOT NULL DEFAULT 'draft',
    notes        TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_quotes_doc UNIQUE (company_id, doc_number)
);
CREATE INDEX ix_quotes_company ON quotes (company_id);
CREATE INDEX ix_quotes_client ON quotes (client_id);
--rollback DROP TABLE quotes;

--changeset erp_maya:013-quote_items
--comment: Detalle de la cotización (renglones de producto con precio y descuento).
CREATE TABLE quote_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies (id),
    quote_id    BIGINT        NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    product_id  BIGINT        REFERENCES products (id),
    quantity    NUMERIC(14,3) NOT NULL,
    unit_price  NUMERIC(14,2) NOT NULL,
    discount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total  NUMERIC(14,2) NOT NULL
);
CREATE INDEX ix_quote_items_quote ON quote_items (quote_id);
CREATE INDEX ix_quote_items_product ON quote_items (product_id);
--rollback DROP TABLE quote_items;
