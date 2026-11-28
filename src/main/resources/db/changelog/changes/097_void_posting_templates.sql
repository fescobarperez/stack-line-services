--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Partida automática de la anulación de una venta.
--
-- La partida de la venta se conserva: es un hecho. La anulación registra
-- su contrapartida con la plantilla asociada a pos.anulacion.contado o
-- pos.anulacion.credito (Contabilidad → Partidas automáticas). Por defecto
-- es el espejo de la venta, con las mismas cuentas posting.* de la empresa.
-- Mismas definiciones que DefaultPostingTemplates (Java).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:097-void-templates
--comment: Plantillas y asociaciones por defecto de la anulación de venta, para las empresas que ya existen. Idempotente.
INSERT INTO journal_templates (company_id, use_case, name, description)
SELECT c.id, v.use_case, v.name, v.description
FROM companies c
CROSS JOIN (VALUES
    ('pos.anulacion.contado', 'Anulación de venta de contado', 'Contrapartida de la venta anulada: sale el dinero de caja y se revierten el ingreso y el IVA.'),
    ('pos.anulacion.credito', 'Anulación de venta al crédito', 'Contrapartida de la venta anulada: el cliente deja de deber y se revierten el ingreso y el IVA.')
) AS v(use_case, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM journal_templates t WHERE t.company_id = c.id AND t.use_case = v.use_case);

INSERT INTO journal_template_lines (template_id, position, side, account_id, uses_document_account, amounts, description, use_cost_center)
SELECT t.id, v.pos, v.side, s.setting_value::bigint, v.doc, v.amounts, v.descr, v.cc
FROM journal_templates t
JOIN (VALUES
    ('pos.anulacion.contado', 1, 'debit',  'posting.revenue',     FALSE, 'base',  'Anulación ingresos {ref}', TRUE),
    ('pos.anulacion.contado', 2, 'debit',  'posting.tax_payable', FALSE, 'iva',   'Anulación IVA {ref}',      TRUE),
    ('pos.anulacion.contado', 3, 'credit', 'posting.cash',        FALSE, 'total', '{descripcion}',            TRUE),
    ('pos.anulacion.credito', 1, 'debit',  'posting.revenue',     FALSE, 'base',  'Anulación ingresos {ref}', TRUE),
    ('pos.anulacion.credito', 2, 'debit',  'posting.tax_payable', FALSE, 'iva',   'Anulación IVA {ref}',      TRUE),
    ('pos.anulacion.credito', 3, 'credit', 'posting.receivable',  FALSE, 'total', '{descripcion}',            TRUE)
) AS v(use_case, pos, side, clave, doc, amounts, descr, cc) ON v.use_case = t.use_case
JOIN company_settings s ON s.company_id = t.company_id AND s.setting_key = v.clave
WHERE NOT EXISTS (SELECT 1 FROM journal_template_lines l WHERE l.template_id = t.id);

INSERT INTO posting_rules (company_id, use_case, template_id)
SELECT t.company_id, t.use_case, t.id
FROM journal_templates t
WHERE t.use_case IN ('pos.anulacion.contado', 'pos.anulacion.credito')
  AND EXISTS (SELECT 1 FROM journal_template_lines l WHERE l.template_id = t.id)
  AND NOT EXISTS (
      SELECT 1 FROM posting_rules r WHERE r.company_id = t.company_id AND r.use_case = t.use_case);
--rollback DELETE FROM posting_rules WHERE use_case LIKE 'pos.anulacion.%'; DELETE FROM journal_template_lines WHERE template_id IN (SELECT id FROM journal_templates WHERE use_case LIKE 'pos.anulacion.%'); DELETE FROM journal_templates WHERE use_case LIKE 'pos.anulacion.%';
