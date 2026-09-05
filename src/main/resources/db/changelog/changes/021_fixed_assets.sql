--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Activos Fijos. Bienes depreciables y su calendario de depreciación.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:021-fixed_assets
--comment: Bien de la empresa (edificio, cómputo, vehículo…). Guarda tasa/vida útil y depreciación acumulada.
CREATE TABLE fixed_assets (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id                BIGINT        NOT NULL REFERENCES companies (id),
    asset_code                VARCHAR(20)   NOT NULL,
    name                      VARCHAR(200)  NOT NULL,
    category                  VARCHAR(40),
    purchase_cost             NUMERIC(16,2) NOT NULL DEFAULT 0,
    acquired_date             DATE,
    serial                    VARCHAR(60),
    branch_id                 BIGINT        REFERENCES branches (id),
    status                    VARCHAR(20)   NOT NULL DEFAULT 'active',
    depreciation_rate         NUMERIC(6,4),
    useful_life_years         INTEGER,
    accumulated_depreciation  NUMERIC(16,2) NOT NULL DEFAULT 0,
    book_value                NUMERIC(16,2),
    notes                     TEXT,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_fixed_assets_code UNIQUE (company_id, asset_code)
);
CREATE INDEX ix_fixed_assets_company ON fixed_assets (company_id);
CREATE INDEX ix_fixed_assets_branch ON fixed_assets (branch_id);
--rollback DROP TABLE fixed_assets;

--changeset erp_maya:021-asset_depreciation
--comment: Cuota de depreciación por período de un activo (monto, acumulado y valor en libros resultante).
CREATE TABLE asset_depreciation (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id           BIGINT        NOT NULL REFERENCES companies (id),
    fixed_asset_id       BIGINT        NOT NULL REFERENCES fixed_assets (id) ON DELETE CASCADE,
    period_date          DATE          NOT NULL,
    depreciation_amount  NUMERIC(16,2) NOT NULL,
    accumulated_amount   NUMERIC(16,2),
    book_value           NUMERIC(16,2),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_asset_depreciation_asset ON asset_depreciation (fixed_asset_id);
--rollback DROP TABLE asset_depreciation;
