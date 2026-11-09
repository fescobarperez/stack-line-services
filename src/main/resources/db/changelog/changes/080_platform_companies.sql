--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Consola de plataforma: historial de estado de cada empresa e invitaciones
-- de activación de cuenta.
--
-- company_status_history  quién cambió el estado, a cuál y por qué. La
--                         primera fila es el alta.
-- user_invitations        enlace de un solo uso para que un usuario invitado
--                         defina su contraseña. Se guarda el SHA-256 del
--                         token, nunca el token. Uno nuevo invalida al anterior.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:080-company-status-history
CREATE TABLE company_status_history (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES companies (id),
    status         VARCHAR(20)  NOT NULL,
    reason         TEXT,
    operator_id    BIGINT       REFERENCES platform_operators (id),
    operator_name  VARCHAR(160) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_company_status_history_company ON company_status_history (company_id, created_at);
-- Las empresas que ya existen arrancan su historial con el estado actual.
INSERT INTO company_status_history (company_id, status, reason, operator_name, created_at)
SELECT id, status, 'Alta de la empresa', 'Sistema', created_at FROM companies;
--rollback DROP TABLE company_status_history;

--changeset stackline:080-user-invitations
CREATE TABLE user_invitations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    company_id     BIGINT       NOT NULL REFERENCES companies (id),
    token_hash     VARCHAR(64)  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    used_at        TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    created_by     VARCHAR(160) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_invitations_token UNIQUE (token_hash)
);
CREATE INDEX ix_user_invitations_user ON user_invitations (user_id);
--rollback DROP TABLE user_invitations;
