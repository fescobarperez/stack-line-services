--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Arreglo del mapeo de contabilización de cobros.
--
-- 053 sembró el mapeo posting.* haciendo JOIN entre company_settings y las
-- cuentas creadas en 053-chart-of-accounts. Pero la cuenta de caja (110101)
-- NO estaba en esa lista —solo 110102 Bancos—, así que el JOIN de
-- 053-posting-map no encontraba 110101 y la fila 'posting.cash' nunca se
-- insertaba. Resultado: registrar un cobro en efectivo revienta con
-- "No hay cuenta contable configurada para 'posting.cash'".
--
-- Aquí se crea 110101 Caja y se re-siembra TODO el mapeo posting.* que
-- falte, para cada empresa. Idempotente: no toca lo que ya exista.
--
-- SEGUNDA CAUSA (058b): en BDs donde 053-chart-of-accounts corrió ANTES de
-- que existiera la empresa, el CROSS JOIN companies no encontró filas y la
-- migración se marcó aplicada habiendo insertado CERO cuentas. Liquibase no
-- lo vuelve a ejecutar. Por eso faltaban 110201 Clientes, 210201 IVA, etc.,
-- y el mapeo de esas cuentas nunca se podía insertar (posting.receivable…).
-- Los changesets 058-full-chart / 058-posting-map-full siembran el catálogo
-- completo y su mapeo de nuevo, idempotentes.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:058-missing-accounts
--comment: Las cuentas que 053-posting-map mapeaba pero 053-chart-of-accounts no creaba (110101 Caja, 410101 Ingresos). Sin ellas el JOIN del mapeo no encajaba.
INSERT INTO accounts (company_id, code, name, level, normal_balance, allows_entries)
SELECT c.id, v.code, v.name, 4, v.saldo, TRUE
FROM companies c
CROSS JOIN (VALUES
    ('110101', 'Caja',                  'debit'),
    ('410101', 'Ingresos por ventas',   'credit')
) AS v(code, name, saldo)
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.company_id = c.id AND a.code = v.code);
--rollback SELECT 1;

--changeset erp_maya:058-posting-map-backfill
--comment: Re-siembra el mapeo posting.* que falte. Mismo mecanismo que 053, ahora que 110101 sí existe.
INSERT INTO company_settings (company_id, setting_key, setting_value)
SELECT a.company_id, m.clave, a.id::text
FROM accounts a
JOIN (VALUES
    ('110101', 'posting.cash'),
    ('110102', 'posting.bank'),
    ('110201', 'posting.receivable'),
    ('410101', 'posting.revenue'),
    ('210201', 'posting.tax_payable'),
    ('110401', 'posting.tax_credit'),
    ('110301', 'posting.inventory'),
    ('510101', 'posting.cost_of_sales'),
    ('210101', 'posting.payable'),
    ('210301', 'posting.customer_advance')
) AS m(code, clave) ON m.code = a.code
WHERE NOT EXISTS (
    SELECT 1 FROM company_settings s
    WHERE s.company_id = a.company_id AND s.setting_key = m.clave);
--rollback SELECT 1;

--changeset erp_maya:058-full-chart
--comment: Catálogo mínimo completo. Repara las BDs donde 053-chart-of-accounts corrió sin empresas y quedó vacío. Idempotente.
INSERT INTO accounts (company_id, code, name, level, normal_balance, allows_entries)
SELECT c.id, v.code, v.name, 4, v.saldo, TRUE
FROM companies c
CROSS JOIN (VALUES
    ('110101', 'Caja',                          'debit'),
    ('110102', 'Bancos',                        'debit'),
    ('110201', 'Clientes',                      'debit'),
    ('110301', 'Inventario de mercaderia',      'debit'),
    ('110401', 'IVA credito fiscal',            'debit'),
    ('410101', 'Ingresos por ventas',           'credit'),
    ('210101', 'Proveedores',                   'credit'),
    ('210201', 'IVA debito fiscal',             'credit'),
    ('210301', 'Anticipos de clientes',         'credit'),
    ('510101', 'Costo de ventas',               'debit')
) AS v(code, name, saldo)
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.company_id = c.id AND a.code = v.code);
--rollback SELECT 1;

--changeset erp_maya:058-posting-map-full
--comment: Mapeo posting.* completo una vez que el catálogo existe. Idempotente.
INSERT INTO company_settings (company_id, setting_key, setting_value)
SELECT a.company_id, m.clave, a.id::text
FROM accounts a
JOIN (VALUES
    ('110101', 'posting.cash'),
    ('110102', 'posting.bank'),
    ('110201', 'posting.receivable'),
    ('410101', 'posting.revenue'),
    ('210201', 'posting.tax_payable'),
    ('110401', 'posting.tax_credit'),
    ('110301', 'posting.inventory'),
    ('510101', 'posting.cost_of_sales'),
    ('210101', 'posting.payable'),
    ('210301', 'posting.customer_advance')
) AS m(code, clave) ON m.code = a.code
WHERE NOT EXISTS (
    SELECT 1 FROM company_settings s
    WHERE s.company_id = a.company_id AND s.setting_key = m.clave);
--rollback SELECT 1;
