--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Auditoría. Bitácora de actividad de usuarios sobre el sistema.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:018-audit_log
--comment: Registro inmutable de actividad (quién, qué acción, en qué módulo/entidad, severidad, IP).
CREATE TABLE audit_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES companies (id),
    user_id      BIGINT       REFERENCES users (id),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    module       VARCHAR(40),
    action       VARCHAR(60),
    severity     VARCHAR(20),
    description  TEXT,
    entity_ref   VARCHAR(60),
    branch_id    BIGINT       REFERENCES branches (id),
    ip_address   VARCHAR(45)
);
CREATE INDEX ix_audit_log_company ON audit_log (company_id);
CREATE INDEX ix_audit_log_user ON audit_log (user_id);
CREATE INDEX ix_audit_log_module ON audit_log (module);
CREATE INDEX ix_audit_log_occurred ON audit_log (occurred_at);
--rollback DROP TABLE audit_log;
