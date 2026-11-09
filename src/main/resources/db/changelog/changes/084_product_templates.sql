--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Plantillas de cálculo por producto.
--
-- Un producto vendible "a la medida" (p. ej. Pérgola) tiene una plantilla que
-- dice cómo se calcula: con los parámetros que da el cliente (ancho,
-- profundidad, alto…) y las opciones de diseño que elige (techo, cielo,
-- acabado…) saca la materia prima en cantidades, su costo, la mano de obra y
-- el texto con el que se dibuja el render.
--
-- No hay tabla de desglose propia: al aplicarse a una cotización, la materia
-- prima entra a project_materials con quote_id y quote_item_id (el proyecto
-- existe desde que nace la cotización), que es de donde la cotización ya saca
-- su costo y el proyecto su lista de compras. La mano de obra entra como cargo
-- de la cotización (quote_charges), porque un servicio no es materia prima.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:084-product-templates
CREATE TABLE product_templates (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    product_id   BIGINT        NOT NULL REFERENCES products (id),
    name         VARCHAR(160)  NOT NULL,
    description  VARCHAR(500),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    version      INTEGER       NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_templates_product UNIQUE (company_id, product_id),
    CONSTRAINT ck_product_templates_name CHECK (length(trim(name)) > 0)
);

-- Lo que se le pregunta al cliente. El código es la variable de las fórmulas.
CREATE TABLE template_params (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    template_id    BIGINT        NOT NULL REFERENCES product_templates (id) ON DELETE CASCADE,
    code           VARCHAR(40)   NOT NULL,
    label          VARCHAR(120)  NOT NULL,
    unit           VARCHAR(20),
    min_value      NUMERIC(12,3),
    max_value      NUMERIC(12,3),
    default_value  NUMERIC(12,3),
    required       BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order     INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_params_code UNIQUE (template_id, code),
    CONSTRAINT ck_template_params_code CHECK (code ~ '^[a-z][a-z0-9_]{0,39}$')
);

-- Opciones de diseño (techo, cielo, acabado) y sus elecciones.
CREATE TABLE template_options (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    template_id  BIGINT        NOT NULL REFERENCES product_templates (id) ON DELETE CASCADE,
    code         VARCHAR(40)   NOT NULL,
    label        VARCHAR(120)  NOT NULL,
    required     BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order   INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_options_code UNIQUE (template_id, code),
    CONSTRAINT ck_template_options_code CHECK (code ~ '^[a-z][a-z0-9_]{0,39}$')
);

CREATE TABLE option_choices (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    option_id    BIGINT        NOT NULL REFERENCES template_options (id) ON DELETE CASCADE,
    code         VARCHAR(40)   NOT NULL,
    label        VARCHAR(120)  NOT NULL,
    -- Cómo se dibuja en el render: "techo de shingle gris sobre plywood".
    render_text  VARCHAR(300),
    -- Qué gana el cliente, para que el agente lo ofrezca: "más fresco, look de casa".
    benefit      VARCHAR(300),
    is_default   BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order   INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_option_choices_code UNIQUE (option_id, code),
    CONSTRAINT ck_option_choices_code CHECK (code ~ '^[a-z][a-z0-9_]{0,39}$')
);

-- Materia prima que lleva. Sin elección = siempre va (la estructura); con
-- elección = solo si el cliente la escoge.
CREATE TABLE template_components (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT        NOT NULL REFERENCES companies (id),
    template_id       BIGINT        NOT NULL REFERENCES product_templates (id) ON DELETE CASCADE,
    choice_id         BIGINT        REFERENCES option_choices (id) ON DELETE CASCADE,
    product_id        BIGINT        NOT NULL REFERENCES products (id),
    -- En la unidad de uso del producto (products.unit), p. ej. (ceil(ancho/1.5)+1)*2.
    quantity_formula  VARCHAR(300)  NOT NULL,
    waste_pct         NUMERIC(5,2)  NOT NULL DEFAULT 0,
    -- Redondear hacia arriba al entero (piezas que no se compran por fracción).
    round_up          BOOLEAN       NOT NULL DEFAULT FALSE,
    group_name        VARCHAR(160)  NOT NULL DEFAULT 'Materiales',
    sort_order        INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT ck_template_components_waste CHECK (waste_pct >= 0 AND waste_pct <= 100)
);
CREATE INDEX ix_template_components_template ON template_components (template_id);

-- Mano de obra y otros cargos que dependen de la medida (Q por m², por poste…).
CREATE TABLE template_charges (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT        NOT NULL REFERENCES companies (id),
    template_id     BIGINT        NOT NULL REFERENCES product_templates (id) ON DELETE CASCADE,
    choice_id       BIGINT        REFERENCES option_choices (id) ON DELETE CASCADE,
    category_code   VARCHAR(40)   NOT NULL DEFAULT 'MANO_OBRA',
    description     VARCHAR(300)  NOT NULL,
    amount_formula  VARCHAR(300)  NOT NULL,
    sort_order      INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_template_charges_template ON template_charges (template_id);

-- Combinaciones armadas (básica, recomendada, premium): {"techo":"shingle", ...}.
CREATE TABLE template_presets (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    template_id   BIGINT        NOT NULL REFERENCES product_templates (id) ON DELETE CASCADE,
    code          VARCHAR(40)   NOT NULL,
    label         VARCHAR(120)  NOT NULL,
    choices_json  TEXT          NOT NULL,
    sort_order    INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_presets_code UNIQUE (template_id, code)
);

-- La línea de la cotización recuerda con qué se calculó, para recalcularla.
ALTER TABLE quote_items ADD COLUMN template_id BIGINT REFERENCES product_templates (id) ON DELETE SET NULL;
ALTER TABLE quote_items ADD COLUMN template_config TEXT;
-- Los cargos que generó una línea calculada se van con ella.
ALTER TABLE quote_charges ADD COLUMN quote_item_id BIGINT REFERENCES quote_items (id) ON DELETE CASCADE;
--rollback ALTER TABLE quote_charges DROP COLUMN quote_item_id;
--rollback ALTER TABLE quote_items DROP COLUMN template_config;
--rollback ALTER TABLE quote_items DROP COLUMN template_id;
--rollback DROP TABLE template_presets;
--rollback DROP TABLE template_charges;
--rollback DROP TABLE template_components;
--rollback DROP TABLE option_choices;
--rollback DROP TABLE template_options;
--rollback DROP TABLE template_params;
--rollback DROP TABLE product_templates;
