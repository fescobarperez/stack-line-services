--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Cotizaciones que nacen fuera del formulario: el asistente comercial.
--
-- origin           quien la creo: NULL = formulario del ERP, 'agente' = asistente.
-- channel          por donde entro la conversacion: 'erp', 'whatsapp', 'api'.
-- conversation_ref id publico de la conversacion en agents-services
--                  (cnv_00000001), para volver del documento al chat.
--
-- Las que crea el asistente entran en estado 'prospecto': se editan igual que
-- un borrador, pero no salen al cliente hasta que alguien las pasa a
-- 'borrador' (enviar a revision). status es texto libre, no hay CHECK que tocar.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:070-quote-origin
ALTER TABLE quotes ADD COLUMN origin VARCHAR(20);
ALTER TABLE quotes ADD COLUMN channel VARCHAR(20);
ALTER TABLE quotes ADD COLUMN conversation_ref VARCHAR(40);
CREATE INDEX idx_quotes_conversation_ref ON quotes (company_id, conversation_ref)
    WHERE conversation_ref IS NOT NULL;
--rollback DROP INDEX IF EXISTS idx_quotes_conversation_ref; ALTER TABLE quotes DROP COLUMN conversation_ref; ALTER TABLE quotes DROP COLUMN channel; ALTER TABLE quotes DROP COLUMN origin;
