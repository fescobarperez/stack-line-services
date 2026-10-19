--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Condiciones comerciales que define el asesor en cada cotización.
--
-- Una condición marcada per_quote (p. ej. «Tiempo de entrega») tiene en
-- quote_conditions.body su valor por defecto; cada cotización nueva lo copia
-- a quote_condition_values y el asesor lo cambia en «Editar borrador». El PDF
-- imprime el valor de la cotización. Cambiar el valor por defecto no toca las
-- cotizaciones que ya existen.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:086-quote-condition-values
ALTER TABLE quote_conditions ADD COLUMN per_quote BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE quote_condition_values (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    quote_id      BIGINT        NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    condition_id  BIGINT        NOT NULL REFERENCES quote_conditions (id) ON DELETE CASCADE,
    value         VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_quote_condition_values UNIQUE (quote_id, condition_id),
    CONSTRAINT ck_quote_condition_values_value CHECK (length(trim(value)) > 0)
);
CREATE INDEX ix_quote_condition_values_quote ON quote_condition_values (company_id, quote_id);
--rollback DROP TABLE quote_condition_values;
--rollback ALTER TABLE quote_conditions DROP COLUMN per_quote;
