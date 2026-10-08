--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Decisión del cliente sobre la cotización enviada.
--
-- sent_version     sube cada vez que la cotización pasa a 'enviada'. Los
--                  botones de WhatsApp llevan esta versión: aprobar un mensaje
--                  viejo (de una versión anterior) se rechaza.
-- client_reason_*  motivo OPCIONAL con el que el cliente rechazó.
-- client_decided_at cuándo aprobó o rechazó el cliente.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:073-quote-client-decision
ALTER TABLE quotes ADD COLUMN sent_version INT NOT NULL DEFAULT 0;
ALTER TABLE quotes ADD COLUMN client_reason_code VARCHAR(40);
ALTER TABLE quotes ADD COLUMN client_reason_note TEXT;
ALTER TABLE quotes ADD COLUMN client_decided_at TIMESTAMPTZ;
UPDATE quotes SET sent_version = 1 WHERE status = 'enviada';
--rollback ALTER TABLE quotes DROP COLUMN client_decided_at; ALTER TABLE quotes DROP COLUMN client_reason_note; ALTER TABLE quotes DROP COLUMN client_reason_code; ALTER TABLE quotes DROP COLUMN sent_version;
