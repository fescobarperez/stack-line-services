--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Proyectos: seguimiento de rentabilidad por trabajo.
--
-- Nacen de una cotización aprobada. El monto contratado se CONGELA al
-- convertir: si después se renegocia el precio, el proyecto conserva con
-- qué se comprometió y el cambio se registra aparte. Sin eso, el margen
-- histórico se reescribiría solo.
--
-- El costo real llega de tres fuentes distintas, y por eso `project_costs`
-- las etiqueta en vez de tener tres tablas:
--   purchase  factura de proveedor imputada al proyecto
--   material  consumo de materia prima, al costo promedio de bodega
--   labor     mano de obra y servicios
--   other     cualquier cargo manual
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:040-projects
--comment: El proyecto. Cuelga de una cotización y de un centro de costo.
CREATE TABLE projects (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES companies (id),
    code               VARCHAR(40)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    quote_id           BIGINT        REFERENCES quotes (id),
    client_id          BIGINT        NOT NULL REFERENCES clients (id),
    cost_center_id     BIGINT        REFERENCES cost_centers (id),
    -- Congelado al convertir: no se recalcula desde la cotización.
    contracted_amount  NUMERIC(16,2) NOT NULL DEFAULT 0,
    currency           VARCHAR(3)    NOT NULL DEFAULT 'GTQ' REFERENCES currencies (code),
    status             VARCHAR(20)   NOT NULL DEFAULT 'open',
    start_date         DATE,
    end_date           DATE,
    notes              TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_code UNIQUE (company_id, code),
    CONSTRAINT ck_projects_status CHECK (status IN ('open', 'closed', 'cancelled'))
);
CREATE INDEX ix_projects_client ON projects (company_id, client_id);
CREATE INDEX ix_projects_status ON projects (company_id, status);
-- Una cotización se convierte UNA vez: la segunda pulsación lleva al proyecto.
CREATE UNIQUE INDEX uq_projects_quote ON projects (quote_id) WHERE quote_id IS NOT NULL;
--rollback DROP TABLE projects;

--changeset erp_maya:040-project-costs
--comment: Cada quetzal gastado, con su origen y su documento de respaldo.
CREATE TABLE project_costs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    project_id    BIGINT        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    source        VARCHAR(20)   NOT NULL,
    ref_id        BIGINT,
    description   VARCHAR(300),
    amount        NUMERIC(16,2) NOT NULL,
    cost_date     DATE          NOT NULL DEFAULT CURRENT_DATE,
    created_by    BIGINT        REFERENCES users (id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_project_costs_source CHECK (source IN ('purchase', 'material', 'labor', 'other'))
);
CREATE INDEX ix_project_costs_project ON project_costs (project_id, source);
--rollback DROP TABLE project_costs;

--changeset erp_maya:040-quote-project-link
--comment: Desde la cotización se llega al proyecto que originó.
ALTER TABLE quotes ADD COLUMN project_id BIGINT REFERENCES projects (id);
--rollback ALTER TABLE quotes DROP COLUMN project_id;
