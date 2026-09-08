--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Catálogo de materia prima · planificación dentro del proyecto (fase 1).
-- Los grupos pertenecen al proyecto, no a una cotización. Una cotización
-- futura podrá referenciarlos sin moverlos ni eliminarlos del proyecto.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:054-project-material-groups
--comment: Árbol de grupos dinámicos para organizar materiales por proyecto.
CREATE TABLE project_material_groups (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES companies (id),
    project_id       BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    parent_group_id  BIGINT       REFERENCES project_material_groups (id) ON DELETE CASCADE,
    name             VARCHAR(160) NOT NULL,
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_project_material_groups_name CHECK (length(trim(name)) > 0)
);
CREATE INDEX ix_project_material_groups_project
    ON project_material_groups (company_id, project_id, parent_group_id, sort_order);
--rollback DROP TABLE project_material_groups;

--changeset erp_maya:054-project-materials
--comment: Materiales planificados con costo estimado automático y trazabilidad operativa.
CREATE TABLE project_materials (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT        NOT NULL REFERENCES companies (id),
    project_id            BIGINT        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    group_id              BIGINT        REFERENCES project_material_groups (id) ON DELETE SET NULL,
    product_id            BIGINT        NOT NULL REFERENCES products (id),
    supplier_id           BIGINT        REFERENCES suppliers (id),
    quantity_planned      NUMERIC(16,3) NOT NULL,
    uom                   VARCHAR(20)   NOT NULL DEFAULT 'unid',
    unit_cost_snapshot    NUMERIC(16,4) NOT NULL DEFAULT 0,
    cost_snapshot_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    quantity_ordered      NUMERIC(16,3) NOT NULL DEFAULT 0,
    quantity_consumed     NUMERIC(16,3) NOT NULL DEFAULT 0,
    status                VARCHAR(24)   NOT NULL DEFAULT 'planned',
    notes                 VARCHAR(500),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_project_materials_quantity CHECK (quantity_planned > 0),
    CONSTRAINT ck_project_materials_status CHECK (status IN ('planned', 'ordered', 'partially_consumed', 'consumed', 'cancelled'))
);
CREATE INDEX ix_project_materials_project ON project_materials (company_id, project_id, group_id);
CREATE INDEX ix_project_materials_product ON project_materials (company_id, product_id);
--rollback DROP TABLE project_materials;
