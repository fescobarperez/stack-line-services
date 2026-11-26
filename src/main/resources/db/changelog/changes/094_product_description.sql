--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Descripción del producto: qué es, de qué está hecho y para qué sirve.
-- La muestran la ficha del producto y los agentes (el planificador y el
-- asistente comercial la usan para hablar de cada modelo). Texto libre y
-- opcional; el tope de largo lo pone ProductRequest.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:094-product-description
ALTER TABLE products ADD COLUMN description TEXT;
--rollback ALTER TABLE products DROP COLUMN description;
