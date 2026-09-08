--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Inversión de dependencia: Proyecto es la entidad central.
-- Una cotización cliente puede pertenecer a un proyecto y un proyecto puede
-- agregar múltiples cotizaciones. RFQ supplier no entra aquí.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:055-project-quotes
--comment: Asociación múltiple y auditable entre proyecto y cotización cliente.
CREATE TABLE project_quotes (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES companies (id),
    project_id        BIGINT        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    quote_id          BIGINT        NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    amount_snapshot   NUMERIC(16,2) NOT NULL DEFAULT 0,
    included          BOOLEAN       NOT NULL DEFAULT FALSE,
    included_at      TIMESTAMPTZ,
    excluded_at      TIMESTAMPTZ,
    exclusion_reason  VARCHAR(300),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_quotes_pair UNIQUE (company_id, project_id, quote_id),
    CONSTRAINT uq_project_quotes_quote UNIQUE (company_id, quote_id)
);
CREATE INDEX ix_project_quotes_project ON project_quotes (company_id, project_id, included);
CREATE INDEX ix_project_quotes_quote ON project_quotes (company_id, quote_id);

-- El vínculo antiguo queda como referencia de compatibilidad, pero ya no
-- restringe a una sola cotización por proyecto.
DROP INDEX IF EXISTS uq_projects_quote;

-- Migra cotizaciones cliente antiguas que todavía no tenían proyecto. Se crea
-- un proyecto legado por cotización para no mezclar clientes ni inventar una
-- asociación aleatoria; las nuevas cotizaciones usarán Proyecto general.
INSERT INTO projects (company_id, code, name, client_id, contracted_amount, currency, status, start_date)
SELECT q.company_id, 'PRY-MIG-' || q.id, 'Proyecto migrado · ' || COALESCE(q.client_name, q.doc_number),
       q.client_id, 0, 'GTQ', 'draft', COALESCE(q.quote_date, CURRENT_DATE)
FROM quotes q
WHERE q.party_type = 'client'
  AND q.project_id IS NULL
  AND q.client_id IS NOT NULL
ON CONFLICT (company_id, code) DO NOTHING;

UPDATE quotes q
SET project_id = p.id
FROM projects p
WHERE q.party_type = 'client'
  AND q.project_id IS NULL
  AND q.client_id IS NOT NULL
  AND p.code = 'PRY-MIG-' || q.id
  AND p.company_id = q.company_id;

-- Migra asociaciones existentes creadas por el flujo anterior.
INSERT INTO project_quotes (company_id, project_id, quote_id, amount_snapshot, included, included_at)
SELECT q.company_id, q.project_id, q.id, q.total,
       q.status = 'aprobada',
       CASE WHEN q.status = 'aprobada' THEN now() ELSE NULL END
FROM quotes q
WHERE q.party_type = 'client'
  AND q.project_id IS NOT NULL
ON CONFLICT (company_id, project_id, quote_id) DO NOTHING;
--rollback DROP TABLE project_quotes;
