--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Plantillas de partidas y su asociación con casos de uso.
--
-- Hasta aquí cada módulo armaba su partida en Java con cuentas fijas y lo
-- único configurable eran las claves posting.* de company_settings, sin
-- pantalla. Ahora la empresa define en Contabilidad → Partidas automáticas:
--
--   journal_templates       qué cuentas mueve una partida
--   journal_template_lines  en qué lado y con qué monto del caso de uso
--   posting_rules           qué plantilla se ejecuta en cada caso de uso
--                           (pos.venta.contado, cxc.cobro…)
--
-- El catálogo de casos de uso vive en el código (PostingCase): cada caso
-- sabe qué montos produce. Ver docs/partidas-automaticas/SPEC.md.
--
-- Además se repara lo que dejaron 053/058: esas migraciones solo sembraron
-- el catálogo y las claves posting.* de las empresas que existían al
-- correr. Las creadas después desde la consola (p. ej. Madera Viva) no
-- tienen ni cuentas ni mapeo, y no pueden registrar una venta.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:095-chart-backfill
--comment: Catálogo mínimo para las empresas creadas después de 058. Mismo contenido que 058-full-chart; idempotente.
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

--changeset erp_maya:095-posting-map-backfill
--comment: Claves posting.* que falten. Mismo contenido que 058-posting-map-full; idempotente. Las plantillas por defecto se arman con ellas.
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

--changeset erp_maya:095-journal-templates
--comment: Plantillas de partidas, sus líneas y la asociación con casos de uso.
CREATE TABLE journal_templates (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    -- Código del caso de uso para el que sirve (PostingCase): decide qué
    -- montos puede usar cada línea.
    use_case    VARCHAR(60)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Sube en cada edición; la partida guarda con qué versión se generó.
    version     INTEGER      NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_journal_templates_company ON journal_templates (company_id, use_case);

CREATE TABLE journal_template_lines (
    id                    BIGSERIAL PRIMARY KEY,
    template_id           BIGINT       NOT NULL REFERENCES journal_templates (id) ON DELETE CASCADE,
    position              INTEGER      NOT NULL,
    side                  VARCHAR(6)   NOT NULL CHECK (side IN ('debit', 'credit')),
    -- Cuenta fija. Con uses_document_account es la de respaldo: se usa si
    -- el documento no trae la suya (p. ej. un cobro en efectivo sin caja elegida).
    account_id            BIGINT       REFERENCES accounts (id),
    uses_document_account BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Claves de los montos del caso de uso que se suman, separadas por coma
    -- ("igss_laboral,igss_patronal"). Sin fórmulas: solo sumas.
    amounts               VARCHAR(200) NOT NULL,
    -- Texto de la línea con variables {descripcion}, {ref}, {tercero}.
    description           VARCHAR(200),
    use_cost_center       BOOLEAN      NOT NULL DEFAULT TRUE,
    CHECK (account_id IS NOT NULL OR uses_document_account)
);
CREATE INDEX ix_journal_template_lines_template ON journal_template_lines (template_id, position);

CREATE TABLE posting_rules (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT      NOT NULL REFERENCES companies (id),
    use_case    VARCHAR(60) NOT NULL,
    template_id BIGINT      NOT NULL REFERENCES journal_templates (id),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_posting_rules UNIQUE (company_id, use_case)
);

-- Auditoría: con qué plantilla y versión nació cada partida automática.
ALTER TABLE journal_entries ADD COLUMN template_id BIGINT REFERENCES journal_templates (id);
ALTER TABLE journal_entries ADD COLUMN template_version INTEGER;
--rollback ALTER TABLE journal_entries DROP COLUMN template_version; ALTER TABLE journal_entries DROP COLUMN template_id; DROP TABLE posting_rules; DROP TABLE journal_template_lines; DROP TABLE journal_templates;

--changeset erp_maya:095-default-templates
--comment: Plantillas por defecto de los casos que ya contabilizaban, con las mismas cuentas posting.* que usa cada empresa: el día del cambio nada se mueve de cuenta. Mismas definiciones que DefaultPostingTemplates (Java), que siembra las empresas nuevas.
INSERT INTO journal_templates (company_id, use_case, name, description)
SELECT c.id, v.use_case, v.name, v.description
FROM companies c
CROSS JOIN (VALUES
    ('pos.venta.contado', 'Venta de contado',            'El dinero entra a caja; el ingreso va sin IVA y el IVA a su cuenta por pagar.'),
    ('pos.venta.credito', 'Venta al crédito',            'El cliente queda debiendo el total; el ingreso va sin IVA y el IVA a su cuenta por pagar.'),
    ('cxc.cobro',         'Cobro de cliente',            'El dinero entra a la caja o al banco del cobro y baja lo que debe el cliente.'),
    ('proyecto.consumo',  'Consumo de material en proyecto', 'El material deja de ser inventario y pasa a costo, en el centro de costo del proyecto.')
) AS v(use_case, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM journal_templates t WHERE t.company_id = c.id AND t.use_case = v.use_case);

INSERT INTO journal_template_lines (template_id, position, side, account_id, uses_document_account, amounts, description, use_cost_center)
SELECT t.id, v.pos, v.side, s.setting_value::bigint, v.doc, v.amounts, v.descr, v.cc
FROM journal_templates t
JOIN (VALUES
    ('pos.venta.contado', 1, 'debit',  'posting.cash',          FALSE, 'total', '{descripcion}',   TRUE),
    ('pos.venta.contado', 2, 'credit', 'posting.revenue',       FALSE, 'base',  'Ingresos {ref}',  TRUE),
    ('pos.venta.contado', 3, 'credit', 'posting.tax_payable',   FALSE, 'iva',   'IVA {ref}',       TRUE),
    ('pos.venta.credito', 1, 'debit',  'posting.receivable',    FALSE, 'total', '{descripcion}',   TRUE),
    ('pos.venta.credito', 2, 'credit', 'posting.revenue',       FALSE, 'base',  'Ingresos {ref}',  TRUE),
    ('pos.venta.credito', 3, 'credit', 'posting.tax_payable',   FALSE, 'iva',   'IVA {ref}',       TRUE),
    ('cxc.cobro',         1, 'debit',  'posting.cash',          TRUE,  'monto', '{descripcion}',   FALSE),
    ('cxc.cobro',         2, 'credit', 'posting.receivable',    FALSE, 'monto', '{descripcion}',   FALSE),
    ('proyecto.consumo',  1, 'debit',  'posting.cost_of_sales', FALSE, 'costo', '{descripcion}',   TRUE),
    ('proyecto.consumo',  2, 'credit', 'posting.inventory',     FALSE, 'costo', '{descripcion}',   TRUE)
) AS v(use_case, pos, side, clave, doc, amounts, descr, cc) ON v.use_case = t.use_case
JOIN company_settings s ON s.company_id = t.company_id AND s.setting_key = v.clave
WHERE NOT EXISTS (SELECT 1 FROM journal_template_lines l WHERE l.template_id = t.id);

INSERT INTO posting_rules (company_id, use_case, template_id)
SELECT t.company_id, t.use_case, t.id
FROM journal_templates t
WHERE t.use_case IN ('pos.venta.contado', 'pos.venta.credito', 'cxc.cobro', 'proyecto.consumo')
  AND EXISTS (SELECT 1 FROM journal_template_lines l WHERE l.template_id = t.id)
  AND NOT EXISTS (
      SELECT 1 FROM posting_rules r WHERE r.company_id = t.company_id AND r.use_case = t.use_case);
--rollback DELETE FROM posting_rules; DELETE FROM journal_template_lines; DELETE FROM journal_templates;
