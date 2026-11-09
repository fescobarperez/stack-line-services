--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Planes, módulos y límites (consola de plataforma, paso 3).
--
-- plans                   catálogo: módulos incluidos (JSONB, siempre con
--                         'base'), límites (NULL = ilimitado) y precio
--                         mensual en quetzales.
-- company_subscriptions   el plan de cada empresa y sus excepciones:
--                         módulos agregados/quitados, límites propios y
--                         precio pactado (NULL = el del plan).
--
-- módulos efectivos = base ∪ (plan ∪ enabled) − disabled
-- límite efectivo   = límite propio ?? límite del plan
--
-- Las empresas que ya existen quedan en 'completo': nada deja de funcionar.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:081-plans
CREATE TABLE plans (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                       VARCHAR(30)   NOT NULL,
    name                       VARCHAR(80)   NOT NULL,
    description                TEXT,
    price                      NUMERIC(12,2) NOT NULL DEFAULT 0,
    modules                    JSONB         NOT NULL DEFAULT '["base"]',
    max_users                  INT,
    max_branches               INT,
    max_fel_per_month          INT,
    max_ai_messages_per_month  INT,
    active                     BOOLEAN       NOT NULL DEFAULT true,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_plans_code UNIQUE (code),
    CONSTRAINT ck_plans_price CHECK (price >= 0)
);
INSERT INTO plans (code, name, description, price, modules, max_users, max_branches, max_fel_per_month, max_ai_messages_per_month) VALUES
 ('basico', 'Básico', 'Venta en mostrador con facturación electrónica.', 450,
  '["base","pos","fel"]', 5, 1, 1000, NULL),
 ('comercial', 'Comercial', 'Inventario, compras y cotizaciones para negocios en crecimiento.', 1200,
  '["base","pos","fel","inventory_plus","purchases","quotes","commercial"]', 15, 3, 3000, NULL),
 ('completo', 'Completo', 'Todo, incluida contabilidad, planilla y el asistente de IA.', 2500,
  '["base","pos","fel","inventory_plus","purchases","quotes","commercial","accounting","payroll","ai"]', NULL, NULL, NULL, 5000);
--rollback DROP TABLE plans;

--changeset stackline:081-company-subscriptions
CREATE TABLE company_subscriptions (
    company_id                 BIGINT        PRIMARY KEY REFERENCES companies (id),
    plan_id                    BIGINT        NOT NULL REFERENCES plans (id),
    price_override             NUMERIC(12,2),
    enabled_modules            JSONB         NOT NULL DEFAULT '[]',
    disabled_modules           JSONB         NOT NULL DEFAULT '[]',
    max_users                  INT,
    max_branches               INT,
    max_fel_per_month          INT,
    max_ai_messages_per_month  INT,
    updated_by                 VARCHAR(160),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_company_subscriptions_price CHECK (price_override IS NULL OR price_override >= 0)
);
CREATE INDEX ix_company_subscriptions_plan ON company_subscriptions (plan_id);
INSERT INTO company_subscriptions (company_id, plan_id, updated_by)
SELECT c.id, p.id, 'Sistema' FROM companies c, plans p WHERE p.code = 'completo';
UPDATE companies SET plan = 'completo';
--rollback DROP TABLE company_subscriptions;
