--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Extensión de presupuesto: departamento en la línea (agrupación del UI:
-- Ingresos, Costo de Ventas, Gastos Administrativos, etc.).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:029-budget-ext
--comment: department en budget_lines para agrupar por departamento.
ALTER TABLE budget_lines ADD COLUMN department VARCHAR(120);
ALTER TABLE budget_lines ALTER COLUMN period_month DROP NOT NULL;
--rollback ALTER TABLE budget_lines DROP COLUMN department;
