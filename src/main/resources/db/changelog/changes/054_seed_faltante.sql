--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Completa lo que una instalación nueva necesita para poder facturar.
--
-- La 053 sembró ocho cuentas dando por hecho que `Caja` y `Ventas` ya
-- existían — y existían en la base de desarrollo original, pero porque
-- alguien las había creado a mano, no porque las pusiera una migración.
-- En una base recién creada faltan, y con ellas faltan dos entradas del
-- mapeo contable.
--
-- El efecto no era un reporte incompleto: como la contabilización va en la
-- misma transacción que la venta, CUALQUIER venta fallaba con «No hay
-- cuenta contable configurada para 'posting.revenue'».
--
-- Lo mismo con la sucursal: sales.branch_id es obligatorio y sin ninguna
-- sucursal no se puede emitir nada.
--
-- No se corrige la 053 porque ya está aplicada; cambiarla rompería su
-- checksum en las bases que la tienen.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:054-cuentas-faltantes
--comment: Caja y Ventas, que la 053 dio por existentes.
INSERT INTO accounts (company_id, code, name, level, normal_balance, allows_entries)
SELECT c.id, v.code, v.name, 4, v.saldo, TRUE
FROM companies c
CROSS JOIN (VALUES
    ('110101', 'Caja',   'debit'),
    ('410101', 'Ventas', 'credit')
) AS v(code, name, saldo)
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.company_id = c.id AND a.code = v.code);
--rollback SELECT 1;

--changeset erp_maya:054-mapeo-faltante
--comment: Las dos entradas del mapeo que quedaron sin cuenta.
INSERT INTO company_settings (company_id, setting_key, setting_value)
SELECT a.company_id, m.clave, a.id::text
FROM accounts a
JOIN (VALUES
    ('110101', 'posting.cash'),
    ('410101', 'posting.revenue')
) AS m(code, clave) ON m.code = a.code
WHERE NOT EXISTS (
    SELECT 1 FROM company_settings s
    WHERE s.company_id = a.company_id AND s.setting_key = m.clave);
--rollback DELETE FROM company_settings WHERE setting_key IN ('posting.cash','posting.revenue');

--changeset erp_maya:054-sucursal-inicial
--comment: Una sucursal para poder emitir documentos.
-- Solo si la empresa no tiene ninguna: no se le agrega una segunda a quien
-- ya configuró las suyas.
INSERT INTO branches (company_id, name, address, status)
SELECT c.id, 'Casa Matriz', NULL, 'active'
FROM companies c
WHERE NOT EXISTS (SELECT 1 FROM branches b WHERE b.company_id = c.id);
--rollback SELECT 1;
