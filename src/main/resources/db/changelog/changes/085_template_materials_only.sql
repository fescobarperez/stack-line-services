--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Las plantillas describen solo la materia prima del producto.
--
-- La mano de obra y los demás costos (transporte, instalación…) se agregan
-- en la cotización como cargos, igual que en cualquier otra; no son parte de
-- cómo está hecho el producto. Se quitan los cargos por plantilla de 084.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:085-template-materials-only
ALTER TABLE quote_charges DROP COLUMN IF EXISTS quote_item_id;
DROP TABLE IF EXISTS template_charges;
--rollback CREATE TABLE template_charges (
--rollback     id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
--rollback     company_id      BIGINT        NOT NULL REFERENCES companies (id),
--rollback     template_id     BIGINT        NOT NULL REFERENCES product_templates (id) ON DELETE CASCADE,
--rollback     choice_id       BIGINT        REFERENCES option_choices (id) ON DELETE CASCADE,
--rollback     category_code   VARCHAR(40)   NOT NULL DEFAULT 'MANO_OBRA',
--rollback     description     VARCHAR(300)  NOT NULL,
--rollback     amount_formula  VARCHAR(300)  NOT NULL,
--rollback     sort_order      INTEGER       NOT NULL DEFAULT 0
--rollback );
--rollback ALTER TABLE quote_charges ADD COLUMN quote_item_id BIGINT REFERENCES quote_items (id) ON DELETE CASCADE;
