--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- El plan de cuentas tiene que ser un árbol completo: una cuenta de nivel
-- N > 1 cuelga de una de nivel N − 1 (AccountTreeRules). No existe un
-- nivel 2 sin su nivel 1.
--
-- El catálogo mínimo (053/054/058/095) sembró solo las cuentas de detalle
-- de nivel 4 (110101 Caja, 410101 Ventas…), sin padre. Aquí se crean sus
-- niveles 1 a 3 y se cuelga cada cuenta huérfana de la de nivel anterior
-- cuyo código es prefijo del suyo. Mismas definiciones que
-- DefaultPostingTemplates.GROUPS (Java), que hace lo mismo al crear una
-- empresa nueva.
--
-- Lo que no se pueda colgar por código (cuentas propias de la empresa con
-- otra numeración) queda como está: el plan de cuentas lo marca y pide
-- su cuenta padre la próxima vez que se edite.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:098-account-tree-groups
--comment: Niveles 1 a 3 del catálogo mínimo, solo en las empresas que tienen alguna de sus cuentas de detalle. Idempotente.
INSERT INTO accounts (company_id, code, name, level, normal_balance, allows_entries)
SELECT c.id, g.code, g.name, g.level, g.saldo, FALSE
FROM companies c
CROSS JOIN (VALUES
    ('1',    'Activo',                  'debit',  1),
    ('11',   'Activo corriente',        'debit',  2),
    ('1101', 'Efectivo y equivalentes', 'debit',  3),
    ('1102', 'Cuentas por cobrar',      'debit',  3),
    ('1103', 'Inventarios',             'debit',  3),
    ('1104', 'Impuestos por cobrar',    'debit',  3),
    ('2',    'Pasivo',                  'credit', 1),
    ('21',   'Pasivo corriente',        'credit', 2),
    ('2101', 'Cuentas por pagar',       'credit', 3),
    ('2102', 'Impuestos por pagar',     'credit', 3),
    ('2103', 'Anticipos recibidos',     'credit', 3),
    ('4',    'Ingresos',                'credit', 1),
    ('41',   'Ingresos de operación',   'credit', 2),
    ('4101', 'Ventas',                  'credit', 3),
    ('5',    'Costos',                  'debit',  1),
    ('51',   'Costo de ventas',         'debit',  2),
    ('5101', 'Costo de ventas',         'debit',  3)
) AS g(code, name, saldo, level)
WHERE EXISTS (
    SELECT 1 FROM accounts d
    WHERE d.company_id = c.id
      AND d.code IN ('110101', '110102', '110201', '110301', '110401',
                     '210101', '210201', '210301', '410101', '510101')
      AND d.code LIKE g.code || '%')
  AND NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.company_id = c.id AND a.code = g.code);
--rollback SELECT 1;

--changeset erp_maya:098-account-tree-link
--comment: Cuelga cada cuenta huérfana de nivel > 1 de la cuenta de nivel anterior de su empresa cuyo código es el prefijo más largo del suyo.
UPDATE accounts a
SET parent_id = (
        SELECT p.id FROM accounts p
        WHERE p.company_id = a.company_id
          AND p.level = a.level - 1
          AND p.code <> a.code
          AND a.code LIKE p.code || '%'
        ORDER BY length(p.code) DESC
        LIMIT 1),
    updated_at = now()
WHERE a.parent_id IS NULL
  AND a.level > 1
  AND EXISTS (
        SELECT 1 FROM accounts p
        WHERE p.company_id = a.company_id
          AND p.level = a.level - 1
          AND p.code <> a.code
          AND a.code LIKE p.code || '%');
--rollback SELECT 1;
