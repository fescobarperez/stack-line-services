--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Ajuste manual de la cotización.
--
-- El último renglón que se negocia: un descuento de cierre, un redondeo
-- pactado, un recargo por urgencia. Va sobre la base ya formada —costo +
-- gastos operativos + ganancia— y ANTES del IVA, porque el impuesto se
-- calcula sobre lo que realmente se le cobra al cliente.
--
-- Puede ser negativo. Lo que no puede es dejar la base bajo cero: eso lo
-- corta MAX(0, …) en el servicio, no un CHECK, porque el límite depende
-- del resto de la cotización y no de la columna.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:067-quote-manual-adjustment
--comment: Ajuste manual sobre la base imponible, positivo o negativo.
ALTER TABLE quotes ADD COLUMN manual_adjustment NUMERIC(14,2) NOT NULL DEFAULT 0;
COMMENT ON COLUMN quotes.manual_adjustment IS
    'Ajuste de cierre sobre la base antes de IVA. Negativo = descuento. '
    'La base resultante nunca baja de cero: lo garantiza QuoteChargeService.';
--rollback ALTER TABLE quotes DROP COLUMN manual_adjustment;
