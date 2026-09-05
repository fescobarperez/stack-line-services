--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Configuración. Ajustes clave-valor por empresa (Config / Mantenimiento).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:019-company_settings
--comment: Preferencias por empresa (impuestos, correlativos, moneda, integraciones…) como clave-valor.
CREATE TABLE company_settings (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES companies (id),
    setting_key    VARCHAR(80)  NOT NULL,
    setting_value  TEXT,
    category       VARCHAR(40),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_settings_key UNIQUE (company_id, setting_key)
);
CREATE INDEX ix_company_settings_company ON company_settings (company_id);
--rollback DROP TABLE company_settings;
