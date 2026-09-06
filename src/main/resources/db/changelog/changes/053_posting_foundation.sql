--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Fundación de la contabilización automática.
--
-- Hasta ahora solo Activos Fijos generaba partidas, contra un plan de
-- cinco cuentas. Ventas, compras, cobros y consumos de inventario no
-- llegaban nunca al libro mayor, así que los cuatro dominios que
-- discutimos —proyecto, facturación, inventario y contabilidad— no podían
-- cuadrar entre sí.
--
-- Aquí van las tres piezas que faltaban: las cuentas donde registrar, el
-- centro de costo en cada línea (sin él no se puede filtrar por proyecto)
-- y el mapeo de qué cuenta usa cada operación.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:053-line-cost-center
--comment: Centro de costo por línea: es lo que permite el reporte por proyecto.
-- Va en la línea y no en la cabecera a propósito: una misma partida puede
-- repartir el gasto entre varios proyectos, y con el centro en la cabecera
-- habría que partirla en dos para lograrlo.
ALTER TABLE journal_entry_lines ADD COLUMN cost_center_id BIGINT REFERENCES cost_centers (id);
ALTER TABLE journal_entry_lines ADD COLUMN description VARCHAR(200);
CREATE INDEX ix_jel_cost_center ON journal_entry_lines (cost_center_id) WHERE cost_center_id IS NOT NULL;
--rollback DROP INDEX ix_jel_cost_center; ALTER TABLE journal_entry_lines DROP COLUMN description; ALTER TABLE journal_entry_lines DROP COLUMN cost_center_id;

--changeset erp_maya:053-entry-reversal
--comment: Qué partida reversa a cuál. Una partida emitida no se borra, se reversa.
ALTER TABLE journal_entries ADD COLUMN reverses_entry_id BIGINT REFERENCES journal_entries (id);
ALTER TABLE journal_entries ADD COLUMN source_id BIGINT;
-- source_type + source_id identifican el documento que originó la partida.
-- El índice único evita el doble asiento: si un reproceso vuelve a pedir la
-- contabilización de la misma venta, revienta en vez de duplicar el ingreso.
CREATE UNIQUE INDEX uq_journal_source ON journal_entries (company_id, source_type, source_id)
    WHERE source_id IS NOT NULL AND reverses_entry_id IS NULL AND status <> 'void';
--rollback DROP INDEX uq_journal_source; ALTER TABLE journal_entries DROP COLUMN source_id; ALTER TABLE journal_entries DROP COLUMN reverses_entry_id;

--changeset erp_maya:053-chart-of-accounts
--comment: Cuentas mínimas para operar. Idempotente: no toca las que ya existan.
INSERT INTO accounts (company_id, code, name, level, normal_balance, allows_entries)
SELECT c.id, v.code, v.name, 4, v.saldo, TRUE
FROM companies c
CROSS JOIN (VALUES
    ('110102', 'Bancos',                          'debit'),
    ('110201', 'Clientes',                        'debit'),
    ('110301', 'Inventario de mercaderia',        'debit'),
    ('110401', 'IVA credito fiscal',              'debit'),
    ('210101', 'Proveedores',                     'credit'),
    ('210201', 'IVA debito fiscal',               'credit'),
    ('210301', 'Anticipos de clientes',           'credit'),
    ('510101', 'Costo de ventas',                 'debit')
) AS v(code, name, saldo)
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.company_id = c.id AND a.code = v.code);
--rollback SELECT 1;

--changeset erp_maya:053-posting-map
--comment: Qué cuenta usa cada operación. Mismo mecanismo que ya usaba Activos Fijos.
-- Se guarda el id y no el código porque es lo que espera el resto del sistema
-- (ver asset_disposal.* en company_settings).
INSERT INTO company_settings (company_id, setting_key, setting_value)
SELECT a.company_id, m.clave, a.id::text
FROM accounts a
JOIN (VALUES
    ('110201', 'posting.receivable'),      -- CxC: lo que deben los clientes
    ('410101', 'posting.revenue'),         -- Ingresos por ventas
    ('210201', 'posting.tax_payable'),     -- IVA que se le cobra al cliente
    ('110401', 'posting.tax_credit'),      -- IVA pagado al proveedor
    ('110301', 'posting.inventory'),
    ('510101', 'posting.cost_of_sales'),
    ('210101', 'posting.payable'),
    ('110101', 'posting.cash'),
    ('110102', 'posting.bank'),
    ('210301', 'posting.customer_advance')
) AS m(code, clave) ON m.code = a.code
WHERE NOT EXISTS (
    SELECT 1 FROM company_settings s
    WHERE s.company_id = a.company_id AND s.setting_key = m.clave);
--rollback DELETE FROM company_settings WHERE setting_key LIKE 'posting.%';
