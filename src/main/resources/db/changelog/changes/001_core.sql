--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Núcleo multi-empresa (SaaS). Raíz del aislamiento por inquilino.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:001-companies
--comment: Empresas inquilinas (tenants) que usan el ERP. Raíz de todo el modelo.
CREATE TABLE companies (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    nit         VARCHAR(20),
    plan        VARCHAR(40)  NOT NULL DEFAULT 'standard',
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_companies_nit UNIQUE (nit)
);
--rollback DROP TABLE companies;

--changeset erp_maya:001-establishments
--comment: Establecimientos SAT (unidad fiscal para FEL). Una empresa registra uno o varios.
CREATE TABLE establishments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES companies (id),
    sat_code         INTEGER      NOT NULL,
    commercial_name  VARCHAR(200),
    address          TEXT,
    phone            VARCHAR(40),
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_establishments_code UNIQUE (company_id, sat_code)
);
CREATE INDEX ix_establishments_company ON establishments (company_id);
--rollback DROP TABLE establishments;
