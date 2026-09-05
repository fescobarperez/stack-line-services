--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Unidades de medida (UOM) y sus factores de conversión.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:015-units_of_measure
--comment: Catálogo de unidades (unidad, caja, docena, kg…). is_base marca la unidad base de su tipo.
CREATE TABLE units_of_measure (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    symbol      VARCHAR(16),
    uom_type    VARCHAR(20),
    is_base     BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_uom_code UNIQUE (company_id, code)
);
CREATE INDEX ix_uom_company ON units_of_measure (company_id);
--rollback DROP TABLE units_of_measure;

--changeset erp_maya:015-uom_conversions
--comment: Factor de conversión entre dos unidades (p.ej. 1 caja = 12 unidades). Origen != destino.
CREATE TABLE uom_conversions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies (id),
    from_uom_id BIGINT        NOT NULL REFERENCES units_of_measure (id),
    to_uom_id   BIGINT        NOT NULL REFERENCES units_of_measure (id),
    factor      NUMERIC(18,6) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_uom_conversion UNIQUE (company_id, from_uom_id, to_uom_id),
    CONSTRAINT ck_uom_conversion_diff CHECK (from_uom_id <> to_uom_id)
);
CREATE INDEX ix_uom_conversions_company ON uom_conversions (company_id);
--rollback DROP TABLE uom_conversions;
