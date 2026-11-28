--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Anulación de ventas.
--
-- Anular una venta la deja en estado 'cancelled' —el que ya excluyen el
-- saldo de clientes (v_client_balance), la antigüedad de CxC y lo
-- facturado de proyectos—, anula su DTE, reversa su partida y devuelve el
-- inventario. Aquí se guarda quién, cuándo y por qué: una anulación sin
-- motivo no se puede auditar.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:096-sale-void
ALTER TABLE sales ADD COLUMN voided_at TIMESTAMPTZ;
ALTER TABLE sales ADD COLUMN void_reason VARCHAR(300);
ALTER TABLE sales ADD COLUMN voided_by BIGINT REFERENCES users (id);
ALTER TABLE fel_documents ADD COLUMN annulled_at TIMESTAMPTZ;
ALTER TABLE fel_documents ADD COLUMN annul_reason VARCHAR(300);
--rollback ALTER TABLE fel_documents DROP COLUMN annul_reason; ALTER TABLE fel_documents DROP COLUMN annulled_at; ALTER TABLE sales DROP COLUMN voided_by; ALTER TABLE sales DROP COLUMN void_reason; ALTER TABLE sales DROP COLUMN voided_at;
