--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Condiciones comerciales de la empresa (mantenimiento en Configuración).
--
-- Textos fijos que salen en cada PDF de cotización, en orden: métodos de
-- pago, garantía, modificaciones… Cada empresa define cuántas quiera; solo
-- las activas se imprimen.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:076-quote-conditions
CREATE TABLE quote_conditions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies (id),
    title       VARCHAR(80)   NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_quote_conditions_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_quote_conditions_body CHECK (length(trim(body)) > 0)
);
CREATE INDEX ix_quote_conditions_company ON quote_conditions (company_id, sort_order);
--rollback DROP TABLE quote_conditions;
