--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Presupuestos. Presupuesto anual y sus líneas por cuenta/mes (ppto vs real).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:024-budgets
--comment: Presupuesto anual de la empresa. execution_pct es el % ejecutado (real/ppto) del año.
CREATE TABLE budgets (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    year           INTEGER       NOT NULL,
    name           VARCHAR(80),
    status         VARCHAR(20)   NOT NULL DEFAULT 'draft',
    total_budget   NUMERIC(16,2) NOT NULL DEFAULT 0,
    execution_pct  NUMERIC(6,2)  NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_budgets_year UNIQUE (company_id, year)
);
CREATE INDEX ix_budgets_company ON budgets (company_id);
--rollback DROP TABLE budgets;

--changeset erp_maya:024-budget_lines
--comment: Línea de presupuesto por cuenta y mes (1-12): monto presupuestado vs real. Normaliza los arreglos mensuales del front.
CREATE TABLE budget_lines (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES companies (id),
    budget_id         BIGINT        NOT NULL REFERENCES budgets (id) ON DELETE CASCADE,
    account_code      VARCHAR(20),
    name              VARCHAR(200),
    is_income         BOOLEAN       NOT NULL DEFAULT FALSE,
    cost_center_id    BIGINT        REFERENCES cost_centers (id),
    period_month      INTEGER       NOT NULL,
    budgeted_amount   NUMERIC(16,2) NOT NULL DEFAULT 0,
    actual_amount     NUMERIC(16,2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_budget_lines UNIQUE (company_id, budget_id, account_code, period_month),
    CONSTRAINT ck_budget_lines_month CHECK (period_month BETWEEN 1 AND 12)
);
CREATE INDEX ix_budget_lines_budget ON budget_lines (budget_id);
CREATE INDEX ix_budget_lines_cost_center ON budget_lines (cost_center_id);
--rollback DROP TABLE budget_lines;
