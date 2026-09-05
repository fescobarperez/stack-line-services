--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Devoluciones. Notas de crédito contra una venta y su detalle.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:012-credit_notes
--comment: Nota de crédito por devolución. Referencia la venta (ticket) y el cliente; define método de reintegro.
CREATE TABLE credit_notes (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    doc_number     VARCHAR(40)   NOT NULL,
    sale_id        BIGINT        REFERENCES sales (id),
    client_id      BIGINT        REFERENCES clients (id),
    return_date    DATE,
    reason         TEXT,
    refund_method  VARCHAR(30),
    total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'issued',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_notes_doc UNIQUE (company_id, doc_number)
);
CREATE INDEX ix_credit_notes_company ON credit_notes (company_id);
CREATE INDEX ix_credit_notes_sale ON credit_notes (sale_id);
CREATE INDEX ix_credit_notes_client ON credit_notes (client_id);
--rollback DROP TABLE credit_notes;

--changeset erp_maya:012-credit_note_items
--comment: Detalle de la nota de crédito: productos devueltos con su precio y monto de línea.
CREATE TABLE credit_note_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT        NOT NULL REFERENCES companies (id),
    credit_note_id  BIGINT        NOT NULL REFERENCES credit_notes (id) ON DELETE CASCADE,
    product_id      BIGINT        REFERENCES products (id),
    quantity        NUMERIC(14,3) NOT NULL,
    unit_price      NUMERIC(14,2) NOT NULL,
    line_total      NUMERIC(14,2) NOT NULL
);
CREATE INDEX ix_credit_note_items_note ON credit_note_items (credit_note_id);
CREATE INDEX ix_credit_note_items_product ON credit_note_items (product_id);
--rollback DROP TABLE credit_note_items;
