--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Autorización por sobrecosto de proyecto (capa 6).
--
-- Reutiliza el motor genérico: un tipo más y sus reglas, sin código nuevo
-- de autorizaciones. El porcentaje que se evalúa es cuánto del contratado
-- consumiría el proyecto CON el cargo nuevo incluido.
--
-- El control va sobre lo que aún se puede evitar —cargos manuales, consumo
-- de material y órdenes de compra—, no sobre la factura del proveedor: esa
-- ya llegó y bloquearla no impide el gasto, solo impide registrarlo.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:043-overrun-type
--comment: Tipo de autorización para gastos que llevan el proyecto sobre lo contratado.
INSERT INTO authorization_types (company_id, code, name, description, resolution_mode)
SELECT c.id, 'project_overrun', 'Sobrecosto de proyecto',
       'Un gasto que llevaría el costo del proyecto por encima de lo contratado con el cliente.',
       'both'
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM authorization_types t
    WHERE t.company_id = c.id AND t.code = 'project_overrun');
--rollback DELETE FROM authorization_types WHERE code = 'project_overrun';

--changeset erp_maya:043-overrun-rules
--comment: Del 90% al 100% del contratado avisa al supervisor; por encima, gerente.
INSERT INTO authorization_rules (company_id, type_id, unit, min_value, max_value, level_id)
SELECT t.company_id, t.id, 'percent', 90, 100, l.id
FROM authorization_types t
JOIN authorization_levels l ON l.company_id = t.company_id AND l.code = 'supervisor'
WHERE t.code = 'project_overrun';

INSERT INTO authorization_rules (company_id, type_id, unit, min_value, max_value, level_id)
SELECT t.company_id, t.id, 'percent', 100, NULL, l.id
FROM authorization_types t
JOIN authorization_levels l ON l.company_id = t.company_id AND l.code = 'gerente'
WHERE t.code = 'project_overrun';
--rollback DELETE FROM authorization_rules WHERE type_id IN (SELECT id FROM authorization_types WHERE code = 'project_overrun');

--changeset erp_maya:043-cost-authorization
--comment: Qué autorización permitió el cargo, cuando hizo falta.
ALTER TABLE project_costs ADD COLUMN authorization_id BIGINT REFERENCES authorization_requests (id);
--rollback ALTER TABLE project_costs DROP COLUMN authorization_id;
