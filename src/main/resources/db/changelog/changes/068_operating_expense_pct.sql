--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Gasto operativo como porcentaje del subtotal.
--
-- Tercer modo de captura, junto a 'single' (un monto) y 'detailed'
-- (partidas). Es la forma en que el negocio lo piensa —«mis gastos
-- operativos son el 12% de lo que vendo»— y evita recapturar el monto
-- cada vez que cambian las líneas de la cotización.
--
-- Los tres modos producen el mismo renglón de la cascada; solo cambia de
-- dónde sale la cifra.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:068-operating-expense-pct
--comment: Porcentaje de gasto operativo y el modo que lo usa.
ALTER TABLE quotes ADD COLUMN operating_expense_pct NUMERIC(7,4) NOT NULL DEFAULT 0;
ALTER TABLE quotes DROP CONSTRAINT ck_quotes_operating_mode;
ALTER TABLE quotes ADD CONSTRAINT ck_quotes_operating_mode
    CHECK (operating_expense_mode IN ('single', 'detailed', 'percent'));
ALTER TABLE quotes ADD CONSTRAINT ck_quotes_operating_pct
    CHECK (operating_expense_pct >= 0 AND operating_expense_pct <= 100);
--rollback ALTER TABLE quotes DROP CONSTRAINT ck_quotes_operating_pct;
--rollback ALTER TABLE quotes DROP CONSTRAINT ck_quotes_operating_mode;
--rollback ALTER TABLE quotes ADD CONSTRAINT ck_quotes_operating_mode CHECK (operating_expense_mode IN ('single', 'detailed'));
--rollback ALTER TABLE quotes DROP COLUMN operating_expense_pct;
