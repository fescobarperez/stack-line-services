--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Categorías de gasto de la cotización.
--
-- quote_charges.category era VARCHAR(80) libre, con un input de texto del
-- lado del front. Así nadie podía sumar "cuánto llevo en gastos
-- operativos" ni comparar la estructura de costo entre cotizaciones: dos
-- trabajos escribían "Mano de obra" y "mano de obra" y eran cosas
-- distintas para la base. En dev, de hecho, los cargos existentes están
-- todos sin categoría.
--
-- `is_operating` marca las categorías que suman al bloque de Gastos
-- Operativos. `protected` marca las que el mantenimiento no deja borrar:
-- el renglón de gastos operativos tiene que existir siempre.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:065-charge-categories
--comment: Catálogo de categorías de gasto por empresa.
CREATE TABLE charge_categories (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES companies (id),
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    is_operating BOOLEAN      NOT NULL DEFAULT FALSE,
    protected    BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    status       VARCHAR(12)  NOT NULL DEFAULT 'active',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_charge_categories_code UNIQUE (company_id, code),
    CONSTRAINT ck_charge_categories_name CHECK (length(trim(name)) > 0)
);
CREATE INDEX ix_charge_categories_company ON charge_categories (company_id, sort_order);
--rollback DROP TABLE charge_categories;

--changeset erp_maya:065-seed-charge-categories
--comment: Gastos operativos es fija y no borrable; el resto son de arranque.
INSERT INTO charge_categories (company_id, code, name, is_operating, protected, sort_order)
SELECT c.id, v.code, v.name, v.is_operating, v.protected, v.sort_order
  FROM companies c
  CROSS JOIN (VALUES
        ('OPERATIVO', 'Gastos operativos', TRUE,  TRUE,  0),
        ('MANO_OBRA', 'Mano de obra',      FALSE, FALSE, 10),
        ('TRANSPORTE','Transporte',        FALSE, FALSE, 20),
        ('OTRO',      'Otros cargos',      FALSE, FALSE, 90)
      ) AS v(code, name, is_operating, protected, sort_order)
 WHERE NOT EXISTS (SELECT 1 FROM charge_categories x
                    WHERE x.company_id = c.id AND x.code = v.code);
--rollback DELETE FROM charge_categories;

--changeset erp_maya:065-quote-charges-category-fk
--comment: quote_charges apunta al catálogo en vez de guardar texto suelto.
ALTER TABLE quote_charges ADD COLUMN category_id BIGINT REFERENCES charge_categories (id);
CREATE INDEX ix_quote_charges_category ON quote_charges (company_id, category_id);

-- Cada texto distinto que ya se haya usado se vuelve categoría propia, para
-- no perder lo capturado. Los que quedaron en NULL siguen sin categoría:
-- inventarles una sería adivinar.
INSERT INTO charge_categories (company_id, code, name, sort_order)
SELECT DISTINCT qc.company_id,
       upper(regexp_replace(trim(qc.category), '[^A-Za-z0-9]+', '_', 'g')),
       trim(qc.category),
       50
  FROM quote_charges qc
 WHERE qc.category IS NOT NULL AND length(trim(qc.category)) > 0
   AND NOT EXISTS (SELECT 1 FROM charge_categories x
                    WHERE x.company_id = qc.company_id
                      AND lower(x.name) = lower(trim(qc.category)));

UPDATE quote_charges qc
   SET category_id = cc.id
  FROM charge_categories cc
 WHERE cc.company_id = qc.company_id
   AND qc.category IS NOT NULL
   AND lower(cc.name) = lower(trim(qc.category));

ALTER TABLE quote_charges DROP COLUMN category;
--rollback ALTER TABLE quote_charges ADD COLUMN category VARCHAR(80);
--rollback DROP INDEX ix_quote_charges_category;
--rollback ALTER TABLE quote_charges DROP COLUMN category_id;
