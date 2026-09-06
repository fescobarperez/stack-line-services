--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Venta a crédito como dato, no como texto.
--
-- Hasta ahora "¿es a crédito?" se respondía con
-- LOWER(payment_method) LIKE '%cred%'. Escribir "Credito " con espacio, o
-- "crédito" con tilde, sacaba la venta de cuentas por cobrar sin que nadie
-- se enterara. Y era la definición que usaban DOS lugares distintos:
-- CreditSaleRepository y v_client_balance.
--
-- `is_credit` es un hecho del documento y no cambia: se vendió al fiado.
-- Si después se cobra, eso lo dicen los abonos, no esta columna. Por eso no
-- se deriva de `status` — ese es el ciclo de vida del DTE, otra cosa.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:048-sale-is-credit
--comment: La venta genera cuenta por cobrar. Se rellena desde el método de pago actual.
ALTER TABLE sales ADD COLUMN is_credit BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE sales SET is_credit = TRUE
 WHERE LOWER(COALESCE(payment_method, '')) LIKE '%cred%';
CREATE INDEX ix_sales_credit ON sales (company_id, is_credit) WHERE is_credit;
COMMENT ON COLUMN sales.is_credit IS
    'La venta se hizo al fiado y genera CxC. No dice si ya se cobró: eso son '
    'los abonos de payments.';
--rollback DROP INDEX ix_sales_credit; ALTER TABLE sales DROP COLUMN is_credit;

--changeset erp_maya:048-client-balance-uses-flag
--comment: El saldo deja de depender de cómo esté escrito el método de pago.
CREATE OR REPLACE VIEW v_client_balance AS
SELECT c.id                                AS client_id,
       c.company_id                        AS company_id,
       c.opening_balance
         + COALESCE(v.charged, 0)
         - COALESCE(p.paid, 0)             AS balance,
       COALESCE(v.charged, 0)              AS charged,
       COALESCE(p.paid, 0)                 AS paid
  FROM clients c
  LEFT JOIN (
        SELECT client_id, SUM(signed_total) AS charged
          FROM sales
         WHERE client_id IS NOT NULL
           AND is_credit
           AND status <> 'cancelled'
         GROUP BY client_id
  ) v ON v.client_id = c.id
  LEFT JOIN (
        SELECT client_id, SUM(amount) AS paid
          FROM payments
         GROUP BY client_id
  ) p ON p.client_id = c.id;
--rollback SELECT 1;

--changeset erp_maya:048-overbill-type
--comment: Facturar por encima de lo contratado en un proyecto.
-- Es distinto de project_overrun: aquel vigila el COSTO contra el contrato,
-- este el INGRESO. Se puede facturar de más legítimamente —una orden de
-- cambio que todavía no se refleja en el contratado— pero no en silencio.
INSERT INTO authorization_types (company_id, code, name, description, resolution_mode)
SELECT c.id, 'project_overbill', 'Facturación sobre contrato',
       'Emitir un documento que lleva lo facturado por encima del monto contratado del proyecto.',
       'both'
FROM companies c
WHERE NOT EXISTS (
    SELECT 1 FROM authorization_types t
    WHERE t.company_id = c.id AND t.code = 'project_overbill');
--rollback DELETE FROM authorization_types WHERE code = 'project_overbill';

--changeset erp_maya:048-overbill-rules
--comment: Por encima del 100% de lo contratado lo aprueba un gerente.
INSERT INTO authorization_rules (company_id, type_id, unit, min_value, max_value, level_id)
SELECT t.company_id, t.id, 'percent', 100, NULL, l.id
FROM authorization_types t
JOIN authorization_levels l ON l.company_id = t.company_id AND l.code = 'gerente'
WHERE t.code = 'project_overbill';
--rollback DELETE FROM authorization_rules WHERE type_id IN (SELECT id FROM authorization_types WHERE code='project_overbill');

--changeset erp_maya:048-sale-item-concept
--comment: Renglón sin producto: el texto de lo que se cobra.
-- product_id ya era nullable, pero sin un texto el renglón salía en blanco:
-- el nombre venía siempre del producto. Es lo que necesitan el anticipo, la
-- estimación por avance y la mano de obra — se le factura al cliente lo
-- contratado, no los materiales que se gastaron.
ALTER TABLE sale_items ADD COLUMN concept VARCHAR(200);
ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_named
    CHECK (product_id IS NOT NULL OR concept IS NOT NULL);
--rollback ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_named; ALTER TABLE sale_items DROP COLUMN concept;
