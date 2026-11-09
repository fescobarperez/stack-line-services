--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Operadores de la plataforma: quienes entran a la consola de Stackline
-- (stack-line-gestion-ui) a administrar empresas, planes e IA. No son
-- usuarios de ninguna empresa: no tienen company_id y su token no sirve en
-- /api/** del ERP (ni el de una empresa en /api/platform/**).
--
-- role             owner | support | viewer
-- status           active | disabled
-- failed_attempts  intentos fallidos seguidos; al 5.º se bloquea 15 min
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:079-platform-operators
CREATE TABLE platform_operators (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email            VARCHAR(160) NOT NULL,
    name             VARCHAR(160) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    password_hash    VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_platform_operators_email UNIQUE (email),
    CONSTRAINT ck_platform_operators_role CHECK (role IN ('owner', 'support', 'viewer')),
    CONSTRAINT ck_platform_operators_status CHECK (status IN ('active', 'disabled'))
);
--rollback DROP TABLE platform_operators;
