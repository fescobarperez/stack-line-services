--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Autenticación real. Código de login por empresa (tenant) para el flujo
-- código de empresa + email + password → JWT con claim company_id.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:025-companies-code
--comment: Código de acceso único por empresa. Es el identificador de tenant en el login.
ALTER TABLE companies ADD COLUMN code VARCHAR(40);
ALTER TABLE companies ADD CONSTRAINT uq_companies_code UNIQUE (code);
--rollback ALTER TABLE companies DROP CONSTRAINT uq_companies_code;
--rollback ALTER TABLE companies DROP COLUMN code;
