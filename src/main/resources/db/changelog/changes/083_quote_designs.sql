--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Diseño que el cliente aprobó con el asistente de WhatsApp.
--
-- Una fila por cotización: la ficha de diseño (lo que el cliente quiere:
-- tipo, medidas, material, detalles) y el render que aprobó. Lo ve el asesor
-- en el detalle de la cotización; no sale en el PDF.
--
-- La imagen va en la base y no en el bucket de logos: es una foto de la casa
-- del cliente y ese bucket sirve archivos públicos.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:083-quote-designs
CREATE TABLE quote_designs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES companies (id),
    quote_id       BIGINT       NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    spec_json      TEXT         NOT NULL,
    render_number  INTEGER,
    render_max     INTEGER,
    image_mime     VARCHAR(40),
    image_data     BYTEA,
    created_by     VARCHAR(120),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_quote_designs_quote UNIQUE (quote_id)
);
CREATE INDEX ix_quote_designs_company ON quote_designs (company_id);
--rollback DROP TABLE quote_designs;
