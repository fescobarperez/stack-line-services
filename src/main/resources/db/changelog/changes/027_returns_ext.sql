--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Extensión de notas de crédito (devoluciones): tipo de NC, datos FEL
-- (estado/uuid/error), cliente denormalizado, cajero/sucursal y renglones
-- de texto libre.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:027-returns-ext
--comment: note_type, ticket_ref, cliente denormalizado, cajero/sucursal, campos FEL e item_name en renglones.
ALTER TABLE credit_notes ADD COLUMN note_type   VARCHAR(20) NOT NULL DEFAULT 'devolucion';
ALTER TABLE credit_notes ADD COLUMN ticket_ref  VARCHAR(40);
ALTER TABLE credit_notes ADD COLUMN client_name VARCHAR(200);
ALTER TABLE credit_notes ADD COLUMN client_nit  VARCHAR(20);
ALTER TABLE credit_notes ADD COLUMN cashier     VARCHAR(120);
ALTER TABLE credit_notes ADD COLUMN branch_name VARCHAR(120);
ALTER TABLE credit_notes ADD COLUMN fel_status  VARCHAR(20) NOT NULL DEFAULT 'pendiente';
ALTER TABLE credit_notes ADD COLUMN fel_uuid    VARCHAR(80);
ALTER TABLE credit_notes ADD COLUMN fel_error   VARCHAR(400);
ALTER TABLE credit_note_items ADD COLUMN item_name VARCHAR(200);
ALTER TABLE credit_note_items ALTER COLUMN unit_price DROP NOT NULL;
--rollback ALTER TABLE credit_note_items DROP COLUMN item_name; ALTER TABLE credit_notes DROP COLUMN note_type; ALTER TABLE credit_notes DROP COLUMN ticket_ref; ALTER TABLE credit_notes DROP COLUMN client_name; ALTER TABLE credit_notes DROP COLUMN client_nit; ALTER TABLE credit_notes DROP COLUMN cashier; ALTER TABLE credit_notes DROP COLUMN branch_name; ALTER TABLE credit_notes DROP COLUMN fel_status; ALTER TABLE credit_notes DROP COLUMN fel_uuid; ALTER TABLE credit_notes DROP COLUMN fel_error;
