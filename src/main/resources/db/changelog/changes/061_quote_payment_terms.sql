--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Plan de pagos de la cotización (modelo cotización-céntrico, Fase 1).
--
-- N cuotas por cotización, cada una con monto (absoluto por ahora) y fecha
-- límite. Los cobros reales (payments.quote_id) se comparan contra este plan.
-- Los porcentajes por cuota quedan para una fase futura.
--
-- Ver diseño: design/cotizacion-centrica-diseno.md (regla 7).
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:061-quote-payment-terms
--comment: Cuotas del plan de pagos de la cotización: monto absoluto + fecha límite.
CREATE TABLE quote_payment_terms (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies (id),
    quote_id    BIGINT        NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
    sequence    INTEGER       NOT NULL DEFAULT 1,
    amount      NUMERIC(16,2) NOT NULL DEFAULT 0,
    due_date    DATE,
    notes       VARCHAR(200),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_quote_payment_terms_amount CHECK (amount >= 0)
);
CREATE INDEX ix_quote_payment_terms_quote ON quote_payment_terms (company_id, quote_id, sequence);
--rollback DROP TABLE quote_payment_terms;
