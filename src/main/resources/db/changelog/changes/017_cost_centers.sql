--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Centros de costo. Agrupan ingresos/gastos para análisis por área.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:017-cost_centers
--comment: Centro de costo (sucursal/área). center_type profit|cost. Un responsable (user) y su presupuesto.
CREATE TABLE cost_centers (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT        NOT NULL REFERENCES companies (id),
    code                  VARCHAR(20)   NOT NULL,
    name                  VARCHAR(160)  NOT NULL,
    cost_group            VARCHAR(60),
    center_type           VARCHAR(20),
    responsible_user_id   BIGINT        REFERENCES users (id),
    budget                NUMERIC(14,2) NOT NULL DEFAULT 0,
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_cost_centers_code UNIQUE (company_id, code)
);
CREATE INDEX ix_cost_centers_company ON cost_centers (company_id);
--rollback DROP TABLE cost_centers;
