--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Motivos de rechazo/ajuste y consultas del cliente sobre una respuesta.
--
-- reason_code  motivo del catálogo (sin_existencia, politica_descuentos, …).
--              El catálogo por defecto vive en el código y cada empresa lo
--              sobreescribe con el ajuste 'quotes.change_reasons' (JSON).
-- parent_id    una 'consulta' apunta a la solicitud sobre la que pregunta.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:072-change-request-reasons
ALTER TABLE quote_change_requests ADD COLUMN reason_code VARCHAR(40);
ALTER TABLE quote_change_requests ADD COLUMN parent_id BIGINT REFERENCES quote_change_requests (id) ON DELETE CASCADE;
--rollback ALTER TABLE quote_change_requests DROP COLUMN parent_id; ALTER TABLE quote_change_requests DROP COLUMN reason_code;
