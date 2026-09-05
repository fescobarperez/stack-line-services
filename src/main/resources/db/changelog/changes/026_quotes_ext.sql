--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Extensión de cotizaciones: RFQ a proveedor (party_type + datos de proveedor),
-- workflow de estado (created_by/bitácora), y renglones de texto libre (item_name
-- + uom) para servicios/insumos sin producto de catálogo.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:026-quotes-ext
--comment: party_type (client|supplier), datos de proveedor/contacto, deadline/leadTime/paymentTerms, item_name/uom en renglones.
ALTER TABLE quotes ADD COLUMN party_type       VARCHAR(20) NOT NULL DEFAULT 'client';
ALTER TABLE quotes ADD COLUMN client_email     VARCHAR(160);
ALTER TABLE quotes ADD COLUMN client_contact   VARCHAR(160);
ALTER TABLE quotes ADD COLUMN supplier_name    VARCHAR(200);
ALTER TABLE quotes ADD COLUMN supplier_nit     VARCHAR(20);
ALTER TABLE quotes ADD COLUMN supplier_email   VARCHAR(160);
ALTER TABLE quotes ADD COLUMN supplier_contact VARCHAR(160);
ALTER TABLE quotes ADD COLUMN deadline         DATE;
ALTER TABLE quotes ADD COLUMN lead_time        VARCHAR(60);
ALTER TABLE quotes ADD COLUMN payment_terms    VARCHAR(60);
ALTER TABLE quotes ADD COLUMN created_by       VARCHAR(120);
ALTER TABLE quote_items ADD COLUMN item_name   VARCHAR(200);
ALTER TABLE quote_items ADD COLUMN uom         VARCHAR(20);
ALTER TABLE quote_items ALTER COLUMN unit_price DROP NOT NULL;
CREATE INDEX ix_quotes_party ON quotes (company_id, party_type);
--rollback DROP INDEX ix_quotes_party; ALTER TABLE quote_items DROP COLUMN item_name; ALTER TABLE quote_items DROP COLUMN uom; ALTER TABLE quotes DROP COLUMN party_type; ALTER TABLE quotes DROP COLUMN client_email; ALTER TABLE quotes DROP COLUMN client_contact; ALTER TABLE quotes DROP COLUMN supplier_name; ALTER TABLE quotes DROP COLUMN supplier_nit; ALTER TABLE quotes DROP COLUMN supplier_email; ALTER TABLE quotes DROP COLUMN supplier_contact; ALTER TABLE quotes DROP COLUMN deadline; ALTER TABLE quotes DROP COLUMN lead_time; ALTER TABLE quotes DROP COLUMN payment_terms; ALTER TABLE quotes DROP COLUMN created_by;

-- ══════════════════════════════════════════════════════════════════════
-- Bitácora genérica de cambios de estado de cotización/RFQ (historial).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:026-quote-history
--comment: Historial de acciones sobre una cotización/RFQ (creada, enviada, aprobada, etc.).
CREATE TABLE quote_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    quote_id    BIGINT       NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    action      VARCHAR(240) NOT NULL,
    actor       VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_quote_history_quote ON quote_history (quote_id);
--rollback DROP TABLE quote_history;
