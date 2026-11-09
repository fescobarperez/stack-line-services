--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Dirección de entrega / instalación de la cotización.
--
-- Antes vivía en las notas (el asistente la anotaba ahí). Como campo propio
-- sale en la tarjeta del cliente del PDF y el vendedor la edita aparte.
-- ══════════════════════════════════════════════════════════════════════

--changeset stackline:077-quote-delivery-address
ALTER TABLE quotes ADD COLUMN delivery_address VARCHAR(300);
--rollback ALTER TABLE quotes DROP COLUMN delivery_address;
