--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- El cobro dice a qué cuenta bancaria entró.
--
-- Efectivo y tarjeta no lo necesitan; transferencia y depósito sí, porque
-- sin eso el dinero "entra" en cuentas por cobrar pero no aparece en ningún
-- banco, y la conciliación bancaria nunca cuadra.
--
-- Al registrar el cobro se crea además el movimiento bancario. Es la misma
-- razón por la que el saldo del cliente pasó a ser derivado en la 046: dos
-- lugares donde anotar el mismo dinero terminan discrepando. Aquí el cobro
-- es el hecho y el movimiento su consecuencia, no un registro paralelo.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:052-payment-bank-account
--comment: Cuenta bancaria del cobro, para transferencia y depósito.
ALTER TABLE payments ADD COLUMN bank_account_id BIGINT REFERENCES bank_accounts (id);
CREATE INDEX ix_payments_bank ON payments (bank_account_id) WHERE bank_account_id IS NOT NULL;
--rollback DROP INDEX ix_payments_bank; ALTER TABLE payments DROP COLUMN bank_account_id;

--changeset erp_maya:052-payment-bank-movement
--comment: El movimiento bancario que generó el cobro, para no duplicarlo.
-- Guardarlo permite dos cosas: no volver a crearlo si el cobro se reprocesa,
-- y poder seguir el rastro del dinero desde el recibo hasta el extracto.
ALTER TABLE payments ADD COLUMN bank_movement_id BIGINT REFERENCES bank_movements (id);
--rollback ALTER TABLE payments DROP COLUMN bank_movement_id;
