--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Facturación Electrónica en Línea (FEL). Documentos tributarios (DTE) de SAT.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:010-fel_documents
--comment: DTE emitido a SAT. Enlaza a la venta origen; guarda UUID/autorización, montos y XML certificado.
CREATE TABLE fel_documents (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT        NOT NULL REFERENCES companies (id),
    sale_id               BIGINT        REFERENCES sales (id),
    dte_type              VARCHAR(10)   NOT NULL,
    series                VARCHAR(12),
    number                VARCHAR(20),
    uuid                  VARCHAR(64),
    authorization_number  VARCHAR(64),
    receptor_name         VARCHAR(200),
    receptor_nit          VARCHAR(20),
    taxable_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    exempt_amount         NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax                   NUMERIC(14,2) NOT NULL DEFAULT 0,
    total                 NUMERIC(14,2) NOT NULL DEFAULT 0,
    status                VARCHAR(20)   NOT NULL DEFAULT 'pending',
    issued_at             TIMESTAMPTZ,
    certified_at          TIMESTAMPTZ,
    xml                   TEXT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_fel_series_number UNIQUE (company_id, dte_type, series, number)
);
CREATE INDEX ix_fel_company ON fel_documents (company_id);
CREATE INDEX ix_fel_sale ON fel_documents (sale_id);
CREATE INDEX ix_fel_uuid ON fel_documents (uuid);
--rollback DROP TABLE fel_documents;
