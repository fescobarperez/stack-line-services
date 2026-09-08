--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Gastos / cargos de la cotización (modelo cotización-céntrico, Fase 1).
--
-- La cotización es el documento operativo. Sus gastos manuales (mano de obra,
-- luz, operativos, ganancia…) viven aquí. El gasto de MATERIALES NO se guarda:
-- se deriva de las líneas (Σ costo de sus materiales) al calcular el total.
--
-- calc_type: 'fixed'   → value es un monto absoluto.
--            'percent' → value es un % que se aplica sobre el subtotal de
--                        costo de la cotización (materiales auto + fixed).
-- computed_amount congela el monto calculado (se recomputa al editar cargos).
--
-- Ver diseño: design/cotizacion-centrica-diseno.md (regla 7).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:060-quote-charges
--comment: Gastos/cargos manuales de la cotización, fixed o percent sobre el subtotal de costo.
CREATE TABLE quote_charges (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT        NOT NULL REFERENCES companies (id),
    quote_id         BIGINT        NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    category         VARCHAR(80),
    description      VARCHAR(300)  NOT NULL,
    calc_type        VARCHAR(10)   NOT NULL DEFAULT 'fixed',
    value            NUMERIC(16,4) NOT NULL DEFAULT 0,
    computed_amount  NUMERIC(16,2) NOT NULL DEFAULT 0,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_quote_charges_calc CHECK (calc_type IN ('fixed', 'percent')),
    CONSTRAINT ck_quote_charges_desc CHECK (length(trim(description)) > 0)
);
CREATE INDEX ix_quote_charges_quote ON quote_charges (company_id, quote_id, sort_order);
--rollback DROP TABLE quote_charges;
