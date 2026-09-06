--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- La venta al cliente asociada al proyecto.
--
-- Cierra el circuito del ingreso. Hasta ahora el proyecto sabía lo COBRADO
-- (payments.project_id) pero no lo FACTURADO, y son cosas distintas: se
-- puede facturar sin cobrar (crédito) y cobrar sin facturar (anticipo).
--
-- Ojo con qué NO se factura: el consumo de materia prima es baja de
-- inventario y costo, nunca un ingreso. Al cliente se le factura lo
-- contratado en la cotización, no los materiales que se gastaron.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:044-sale-project
--comment: Ventas (DTE) emitidas contra el proyecto.
ALTER TABLE sales ADD COLUMN project_id BIGINT REFERENCES projects (id);
CREATE INDEX ix_sales_project ON sales (project_id);
--rollback DROP INDEX ix_sales_project; ALTER TABLE sales DROP COLUMN project_id;
