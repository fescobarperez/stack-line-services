--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Marketing. Reglas de promoción (target polimórfico por ahora).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:009-promotions
--comment: Reglas de descuento (2x1, 3x2, combo, % off). target polimórfico por texto en esta fase.
CREATE TABLE promotions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    name        VARCHAR(200) NOT NULL,
    promo_type  VARCHAR(30),
    target      VARCHAR(200),
    valid       VARCHAR(80),
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_promotions_company ON promotions (company_id);
--rollback DROP TABLE promotions;
