--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Traslados entre sucursales. Doble FK a branches (origen y destino).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:006-transfers
--comment: Traslado de stock entre tiendas. from/to apuntan ambas a branches; origen != destino.
CREATE TABLE transfers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES companies (id),
    doc_number      VARCHAR(40)  NOT NULL,
    from_branch_id  BIGINT       NOT NULL REFERENCES branches (id),
    to_branch_id    BIGINT       NOT NULL REFERENCES branches (id),
    transporter     VARCHAR(160),
    transfer_date   DATE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'draft',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_doc UNIQUE (company_id, doc_number),
    CONSTRAINT ck_transfers_branches CHECK (from_branch_id <> to_branch_id)
);
CREATE INDEX ix_transfers_company ON transfers (company_id);
CREATE INDEX ix_transfers_from ON transfers (from_branch_id);
CREATE INDEX ix_transfers_to ON transfers (to_branch_id);
--rollback DROP TABLE transfers;

--changeset erp_maya:006-transfer_items
--comment: Detalle del traslado. quantity vs qty_received detecta mermas/faltantes en tránsito.
CREATE TABLE transfer_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    transfer_id   BIGINT        NOT NULL REFERENCES transfers (id) ON DELETE CASCADE,
    product_id    BIGINT        REFERENCES products (id),
    quantity      NUMERIC(14,3) NOT NULL,
    qty_received  NUMERIC(14,3) NOT NULL DEFAULT 0
);
CREATE INDEX ix_transfer_items_transfer ON transfer_items (transfer_id);
CREATE INDEX ix_transfer_items_product ON transfer_items (product_id);
--rollback DROP TABLE transfer_items;
