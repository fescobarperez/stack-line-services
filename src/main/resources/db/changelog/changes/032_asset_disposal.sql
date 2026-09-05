--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Baja de activos fijos: fecha de baja y enlace a la póliza contable
-- generada automáticamente (partida de disposición).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:032-asset-disposal
--comment: disposal_date + disposal_journal_entry_id en fixed_assets (partida de baja).
ALTER TABLE fixed_assets ADD COLUMN disposal_date            DATE;
ALTER TABLE fixed_assets ADD COLUMN disposal_journal_entry_id BIGINT REFERENCES journal_entries (id);
--rollback ALTER TABLE fixed_assets DROP COLUMN disposal_date; ALTER TABLE fixed_assets DROP COLUMN disposal_journal_entry_id;
