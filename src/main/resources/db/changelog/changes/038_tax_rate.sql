--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Tasa de IVA configurable e histórica.
--
-- Estaba quemada como constante en QuoteService, SaleService y
-- ReportsService, y el campo "Tasa IVA" de Configuración no lo leía nadie.
--
-- Además de hacerla configurable hay que congelarla en cada documento: si
-- mañana la tasa cambia, una factura de hoy debe seguir mostrando la que se
-- le aplicó. Recalcularla al vuelo falsearía documentos ya emitidos.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:038-tax-rate-setting
--comment: Valor por defecto por empresa. Porcentaje, no fracción.
INSERT INTO company_settings (company_id, setting_key, setting_value, category)
SELECT c.id, 'tax.iva_rate', '12', 'taxes'
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM company_settings s
    WHERE s.company_id = c.id AND s.setting_key = 'tax.iva_rate');
--rollback DELETE FROM company_settings WHERE setting_key = 'tax.iva_rate';

--changeset erp_maya:038-document-tax-rate
--comment: La tasa aplicada queda congelada en el documento.
ALTER TABLE sales  ADD COLUMN tax_rate NUMERIC(6,3) NOT NULL DEFAULT 12;
ALTER TABLE quotes ADD COLUMN tax_rate NUMERIC(6,3) NOT NULL DEFAULT 12;
--rollback ALTER TABLE sales DROP COLUMN tax_rate; ALTER TABLE quotes DROP COLUMN tax_rate;
