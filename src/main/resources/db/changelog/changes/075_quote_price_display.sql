--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Presentación de precios de la cotización al cliente.
--
-- quotes.price_display        'detailed' (precio por línea, como siempre) o
--                             'global' (solo el total del proyecto; las líneas
--                             salen como «Incluido» sin precio). El valor por
--                             defecto de cada empresa vive en company_settings
--                             (quotes.price_display).
-- quote_items.show_to_client  con 'global', las líneas internas (p. ej.
--                             materiales) no aparecen en el PDF. En 'detailed'
--                             todas se muestran, para que los montos cuadren.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:075-quote-price-display
ALTER TABLE quotes ADD COLUMN price_display VARCHAR(20) NOT NULL DEFAULT 'detailed';
ALTER TABLE quote_items ADD COLUMN show_to_client BOOLEAN NOT NULL DEFAULT TRUE;
--rollback ALTER TABLE quote_items DROP COLUMN show_to_client; ALTER TABLE quotes DROP COLUMN price_display;
