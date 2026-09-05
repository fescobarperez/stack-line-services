--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Motor de autorizaciones. Genérico a propósito: no sabe qué es un
-- descuento. El módulo que pide (POS, notas de crédito, pagos…) manda
-- tipo, monto o porcentaje, sucursal y un payload que el motor guarda sin
-- interpretar y devuelve al aprobarse.
--
-- Regla de enganche: el consumidor NUNCA decide si algo requiere
-- autorización, siempre pregunta. Añadir un caso nuevo es una fila en
-- `authorization_rules`, no código.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:036-authorization-types
--comment: Qué se puede autorizar y cómo se resuelve (PIN en sitio, bandeja, o ambos).
CREATE TABLE authorization_types (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES companies (id),
    code            VARCHAR(60)  NOT NULL,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    resolution_mode VARCHAR(10)  NOT NULL DEFAULT 'both',
    active          BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_auth_types_code UNIQUE (company_id, code),
    CONSTRAINT ck_auth_types_mode CHECK (resolution_mode IN ('pin', 'tray', 'both'))
);
--rollback DROP TABLE authorization_types;

--changeset erp_maya:036-authorization-rules
--comment: Umbrales. Por porcentaje o por monto; el de monto lleva moneda siempre.
CREATE TABLE authorization_rules (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    type_id      BIGINT        NOT NULL REFERENCES authorization_types (id) ON DELETE CASCADE,
    unit         VARCHAR(10)   NOT NULL,
    min_value    NUMERIC(14,3) NOT NULL DEFAULT 0,
    max_value    NUMERIC(14,3),
    currency     VARCHAR(3)    REFERENCES currencies (code),
    level_id     BIGINT        NOT NULL REFERENCES authorization_levels (id),
    active       BOOLEAN       NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_rules_unit CHECK (unit IN ('percent', 'amount')),
    -- Un umbral en dinero sin moneda es ambiguo; uno en % no la necesita.
    CONSTRAINT ck_auth_rules_currency CHECK (
        (unit = 'amount' AND currency IS NOT NULL) OR (unit = 'percent' AND currency IS NULL)),
    CONSTRAINT ck_auth_rules_band CHECK (max_value IS NULL OR max_value > min_value)
);
CREATE INDEX ix_auth_rules_type ON authorization_rules (company_id, type_id, active);
--rollback DROP TABLE authorization_rules;

--changeset erp_maya:036-authorization-requests
--comment: La solicitud. `payload` lo guarda el motor sin interpretarlo.
CREATE TABLE authorization_requests (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    type_id        BIGINT        NOT NULL REFERENCES authorization_types (id),
    rule_id        BIGINT        REFERENCES authorization_rules (id),
    requested_by   BIGINT        NOT NULL REFERENCES users (id),
    branch_id      BIGINT        REFERENCES branches (id),
    amount         NUMERIC(14,2),
    currency       VARCHAR(3)    REFERENCES currencies (code),
    percent        NUMERIC(6,3),
    payload        JSONB         NOT NULL DEFAULT '{}',
    status         VARCHAR(12)   NOT NULL DEFAULT 'pending',
    resolution_mode VARCHAR(10)  NOT NULL,
    reference      VARCHAR(80),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ,
    CONSTRAINT ck_auth_req_status CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled'))
);
CREATE INDEX ix_auth_req_pending ON authorization_requests (company_id, status, branch_id);
CREATE INDEX ix_auth_req_user    ON authorization_requests (requested_by);
--rollback DROP TABLE authorization_requests;

--changeset erp_maya:036-authorization-steps
--comment: Traza de decisiones. Una fila por intervención: quién, qué decidió y cuándo.
CREATE TABLE authorization_steps (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES companies (id),
    request_id   BIGINT       NOT NULL REFERENCES authorization_requests (id) ON DELETE CASCADE,
    approver_id  BIGINT       NOT NULL REFERENCES users (id),
    decision     VARCHAR(12)  NOT NULL,
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_steps_decision CHECK (decision IN ('approved', 'rejected'))
);
CREATE INDEX ix_auth_steps_request ON authorization_steps (request_id);
--rollback DROP TABLE authorization_steps;

--changeset erp_maya:036-seed-pos-discount
--comment: Primer caso: descuento manual en POS. Los de promoción no pasan por aquí.
INSERT INTO authorization_types (company_id, code, name, description, resolution_mode)
SELECT c.id, 'pos_discount', 'Descuento manual en POS',
       'Descuento de ingreso libre en el punto de venta. Los descuentos de promociones configuradas no requieren autorización.',
       'both'
FROM companies c;

-- Sobre 10% pide supervisor; sobre 30%, gerente. Editable desde la UI.
INSERT INTO authorization_rules (company_id, type_id, unit, min_value, max_value, level_id)
SELECT t.company_id, t.id, 'percent', 10, 30, l.id
FROM authorization_types t
JOIN authorization_levels l ON l.company_id = t.company_id AND l.code = 'supervisor'
WHERE t.code = 'pos_discount';

INSERT INTO authorization_rules (company_id, type_id, unit, min_value, max_value, level_id)
SELECT t.company_id, t.id, 'percent', 30, NULL, l.id
FROM authorization_types t
JOIN authorization_levels l ON l.company_id = t.company_id AND l.code = 'gerente'
WHERE t.code = 'pos_discount';
--rollback DELETE FROM authorization_rules; DELETE FROM authorization_types WHERE code = 'pos_discount';
