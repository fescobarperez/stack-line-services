--liquibase formatted sql
-- Modelo de precio: costos operativos + ganancia explícita + IVA.

--changeset erp_maya:062-quote-pricing
ALTER TABLE quotes
    ADD COLUMN profit_calc_type VARCHAR(10) NOT NULL DEFAULT 'fixed',
    ADD COLUMN profit_value NUMERIC(16,4) NOT NULL DEFAULT 0,
    ADD COLUMN profit_amount NUMERIC(16,2) NOT NULL DEFAULT 0;

ALTER TABLE quotes
    ADD CONSTRAINT ck_quotes_profit_calc CHECK (profit_calc_type IN ('fixed', 'percent'));

-- Los cargos manuales existentes se conservan y pasan a representar costos
-- operativos. La ganancia deja de vivir en quote_charges.
--rollback ALTER TABLE quotes DROP CONSTRAINT ck_quotes_profit_calc; ALTER TABLE quotes DROP COLUMN profit_calc_type; ALTER TABLE quotes DROP COLUMN profit_value; ALTER TABLE quotes DROP COLUMN profit_amount;
