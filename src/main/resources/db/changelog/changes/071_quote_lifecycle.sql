--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Ciclo de vida de las cotizaciones del asistente y solicitudes de cambio.
--
-- Estados (status es texto libre, no hay CHECK):
--   abierta    la arma el asistente con el cliente; solo el asistente la edita
--   prospecto  el cliente la dio por terminada; espera que un vendedor la abra
--   borrador…  un vendedor la abrió (taken_by/taken_at): desde ahí es suya
--   abandonada abierta sin actividad; el proceso de limpieza la cierra
--
-- Un prospecto vuelve a 'abierta' si el cliente lo pide y NINGÚN vendedor lo
-- ha abierto (taken_at nulo). Ya tomada, el cliente solo deja solicitudes de
-- cambio que el vendedor acepta, ajusta o rechaza.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:071-quote-taken
ALTER TABLE quotes ADD COLUMN taken_by VARCHAR(120);
ALTER TABLE quotes ADD COLUMN taken_at TIMESTAMPTZ;
--rollback ALTER TABLE quotes DROP COLUMN taken_at; ALTER TABLE quotes DROP COLUMN taken_by;

--changeset stackline:071-quote-change-requests
CREATE TABLE quote_change_requests (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT       NOT NULL,
    quote_id              BIGINT       NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    -- agregar | quitar | cantidad | descuento | condiciones | otro
    kind                  VARCHAR(20)  NOT NULL,
    product_id            BIGINT,
    product_name          VARCHAR(255),
    quantity              NUMERIC(14, 4),
    discount_pct          NUMERIC(5, 2),
    -- lo que pidió el cliente, con sus palabras
    detail                TEXT,
    -- pendiente | aplicada | ajustada | rechazada | respondida
    status                VARCHAR(20)  NOT NULL DEFAULT 'pendiente',
    response              TEXT,
    adjusted_quantity     NUMERIC(14, 4),
    adjusted_discount_pct NUMERIC(5, 2),
    source                VARCHAR(20),
    requested_by          VARCHAR(120),
    resolved_by           VARCHAR(120),
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_quote_change_requests_quote ON quote_change_requests (company_id, quote_id, status);
--rollback DROP TABLE quote_change_requests;

--changeset stackline:071-quote-notifications
-- Bandeja de salida hacia agents-services: se escribe en la misma transacción
-- que el cambio y un proceso la entrega con reintentos. Si agents-services
-- está caído, el aviso al cliente espera; no se pierde.
CREATE TABLE quote_notifications (
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT       NOT NULL,
    quote_id         BIGINT       NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    kind             VARCHAR(30)  NOT NULL,
    channel          VARCHAR(20),
    conversation_ref VARCHAR(40),
    payload          TEXT         NOT NULL,
    -- pendiente | enviada | fallida
    status           VARCHAR(20)  NOT NULL DEFAULT 'pendiente',
    attempts         INT          NOT NULL DEFAULT 0,
    last_error       TEXT,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ
);
CREATE INDEX idx_quote_notifications_pending ON quote_notifications (next_attempt_at)
    WHERE status = 'pendiente';
--rollback DROP TABLE quote_notifications;

--changeset stackline:071-prospectos-abiertos
-- Los prospectos que el asistente dejó sin terminar con el modelo anterior
-- (sin cliente que confirmara) pasan a 'abierta'; los que ya tienen PDF
-- enviado se quedan como prospecto.
UPDATE quotes q SET status = 'abierta'
 WHERE q.origin = 'agente' AND q.status = 'prospecto'
   AND NOT EXISTS (SELECT 1 FROM quote_history h
                    WHERE h.quote_id = q.id AND h.action LIKE 'PDF preliminar enviado%');
--rollback UPDATE quotes SET status = 'prospecto' WHERE origin = 'agente' AND status = 'abierta';
