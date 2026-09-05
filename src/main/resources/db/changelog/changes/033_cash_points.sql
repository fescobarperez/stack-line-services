--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Cajas físicas y control de turno.
--
-- Hasta ahora `cash_registers` mezclaba dos conceptos: la CAJA (el cajón,
-- recurso duradero de la sucursal) y el TURNO (la sesión apertura→arqueo→
-- cierre de un cajero). Sin la caja como entidad no se podía expresar la
-- regla "una sucursal tiene N cajas y N cajeros, pero cada caja la usa un
-- solo cajero a la vez".
--
-- Aquí `cash_points` pasa a ser la caja y `cash_registers` queda como el
-- turno, que es lo que sus columnas ya describían (opened_at, arqueo…).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:033-cash-points
--comment: Caja física (estación de cobro). N por sucursal, sin límite.
CREATE TABLE cash_points (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    branch_id   BIGINT       NOT NULL REFERENCES branches (id),
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(160) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_cash_points_code UNIQUE (company_id, branch_id, code)
);
CREATE INDEX ix_cash_points_company ON cash_points (company_id);
CREATE INDEX ix_cash_points_branch  ON cash_points (branch_id);
--rollback DROP TABLE cash_points;

--changeset erp_maya:033-cash-points-seed
--comment: Una caja por sucursal existente, para que los turnos actuales tengan a qué apuntar.
INSERT INTO cash_points (company_id, branch_id, code, name)
SELECT b.company_id, b.id, 'CAJA-01', 'Caja 01'
FROM branches b;
--rollback DELETE FROM cash_points WHERE code = 'CAJA-01';

--changeset erp_maya:033-session-columns
--comment: El turno cuelga de una caja y de una fecha operativa, no solo de un instante.
ALTER TABLE cash_registers ADD COLUMN cash_point_id BIGINT REFERENCES cash_points (id);
ALTER TABLE cash_registers ADD COLUMN business_date DATE;
--rollback ALTER TABLE cash_registers DROP COLUMN cash_point_id; ALTER TABLE cash_registers DROP COLUMN business_date;

--changeset erp_maya:033-session-backfill
--comment: Turnos existentes -> la caja única de su sucursal; fecha operativa = día de apertura.
UPDATE cash_registers cr
SET cash_point_id = cp.id
FROM cash_points cp
WHERE cp.branch_id = cr.branch_id AND cp.company_id = cr.company_id
  AND cr.cash_point_id IS NULL;

UPDATE cash_registers
SET business_date = COALESCE(date(opened_at), date(created_at))
WHERE business_date IS NULL;
--rollback UPDATE cash_registers SET cash_point_id = NULL, business_date = NULL;

--changeset erp_maya:033-session-notnull
--comment: Ya rellenados, pasan a obligatorios.
ALTER TABLE cash_registers ALTER COLUMN cash_point_id SET NOT NULL;
ALTER TABLE cash_registers ALTER COLUMN business_date SET NOT NULL;
--rollback ALTER TABLE cash_registers ALTER COLUMN cash_point_id DROP NOT NULL; ALTER TABLE cash_registers ALTER COLUMN business_date DROP NOT NULL;

--changeset erp_maya:033-session-close-stale
--comment: Turnos abiertos duplicados por caja (datos previos sin la regla): se dejan solo el más reciente.
UPDATE cash_registers SET status = 'abandoned', closed_at = now()
WHERE status = 'open' AND id NOT IN (
    SELECT DISTINCT ON (cash_point_id) id
    FROM cash_registers WHERE status = 'open'
    ORDER BY cash_point_id, opened_at DESC NULLS LAST, id DESC
);
--rollback UPDATE cash_registers SET status = 'open', closed_at = NULL WHERE status = 'abandoned';

--changeset erp_maya:033-session-unique
--comment: LA regla, garantizada por la base: una sesión abierta por caja y una por cajero.
CREATE UNIQUE INDEX uq_cash_session_open_per_point
    ON cash_registers (cash_point_id) WHERE status = 'open';
CREATE UNIQUE INDEX uq_cash_session_open_per_user
    ON cash_registers (user_id) WHERE status = 'open' AND user_id IS NOT NULL;
CREATE INDEX ix_cash_registers_business_date ON cash_registers (company_id, business_date);
--rollback DROP INDEX uq_cash_session_open_per_point; DROP INDEX uq_cash_session_open_per_user; DROP INDEX ix_cash_registers_business_date;

--changeset erp_maya:033-sale-register-required
--comment: Una venta de POS pertenece siempre a un turno; sin turno no hay arqueo posible.
CREATE INDEX IF NOT EXISTS ix_sales_cash_register ON sales (cash_register_id);
--rollback DROP INDEX ix_sales_cash_register;
