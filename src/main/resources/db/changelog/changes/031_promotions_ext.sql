--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Motor de promociones: reglas ricas (tipo/valor/condiciones/vigencia) y
-- tabla de usos (tracking) para las métricas de efectividad.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:031-promotions-ext
--comment: campos de regla en promotions (value, condiciones, horario, nxm, vigencia, descripción).
ALTER TABLE promotions ADD COLUMN value        NUMERIC(14,2);
ALTER TABLE promotions ADD COLUMN category     VARCHAR(120);
ALTER TABLE promotions ADD COLUMN product      VARCHAR(200);
ALTER TABLE promotions ADD COLUMN client_type  VARCHAR(60);
ALTER TABLE promotions ADD COLUMN branches     VARCHAR(200);
ALTER TABLE promotions ADD COLUMN days         VARCHAR(40);
ALTER TABLE promotions ADD COLUMN hora_inicio  VARCHAR(10);
ALTER TABLE promotions ADD COLUMN hora_fin     VARCHAR(10);
ALTER TABLE promotions ADD COLUMN min_compra   NUMERIC(14,2);
ALTER TABLE promotions ADD COLUMN nxm_n        INTEGER;
ALTER TABLE promotions ADD COLUMN nxm_m        INTEGER;
ALTER TABLE promotions ADD COLUMN date_start   DATE;
ALTER TABLE promotions ADD COLUMN date_end     DATE;
ALTER TABLE promotions ADD COLUMN description  TEXT;
--rollback ALTER TABLE promotions DROP COLUMN value; ALTER TABLE promotions DROP COLUMN category; ALTER TABLE promotions DROP COLUMN product; ALTER TABLE promotions DROP COLUMN client_type; ALTER TABLE promotions DROP COLUMN branches; ALTER TABLE promotions DROP COLUMN days; ALTER TABLE promotions DROP COLUMN hora_inicio; ALTER TABLE promotions DROP COLUMN hora_fin; ALTER TABLE promotions DROP COLUMN min_compra; ALTER TABLE promotions DROP COLUMN nxm_n; ALTER TABLE promotions DROP COLUMN nxm_m; ALTER TABLE promotions DROP COLUMN date_start; ALTER TABLE promotions DROP COLUMN date_end; ALTER TABLE promotions DROP COLUMN description;

--changeset erp_maya:031-promotion-usage
--comment: Tracking de aplicaciones de promoción (usos, ahorro, ticket) para métricas de efectividad.
CREATE TABLE promotion_usage (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    promotion_id  BIGINT        NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    sale_id       BIGINT,
    amount_saved  NUMERIC(14,2) NOT NULL DEFAULT 0,
    reference     VARCHAR(60),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_promotion_usage_promo ON promotion_usage (promotion_id);
--rollback DROP TABLE promotion_usage;
