--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Cómo se captura el gasto operativo de cada cotización.
--
-- 'single'   un monto único, para un trabajo chico que no merece desglose
-- 'detailed' partidas separadas (energía, alquiler, transporte…)
--
-- Va por cotización y no como configuración global porque la misma empresa
-- cotiza trabajos de Q800 y de Q80,000, y obligar al desglose en el primero
-- solo hace que nadie lo llene.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:066-operating-expense-mode
--comment: Modo de captura del gasto operativo, por cotización.
ALTER TABLE quotes ADD COLUMN operating_expense_mode VARCHAR(10) NOT NULL DEFAULT 'single';
ALTER TABLE quotes ADD CONSTRAINT ck_quotes_operating_mode
    CHECK (operating_expense_mode IN ('single', 'detailed'));
--rollback ALTER TABLE quotes DROP CONSTRAINT ck_quotes_operating_mode;
--rollback ALTER TABLE quotes DROP COLUMN operating_expense_mode;
