--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Saldo del cliente: de contador almacenado a valor derivado.
--
-- `clients.balance` era un contador que solo sabía bajar: PaymentService lo
-- restaba en cada abono y nadie lo sumaba al facturar. Por eso Ferretería
-- López aparecía en -40 teniendo una venta a crédito de 100 con un abono de
-- 40 — su saldo real son 60.
--
-- Al mismo tiempo `ReceivableAgingService` ya calculaba bien el saldo, por
-- documento y con antigüedad, desde ventas y abonos. Había dos definiciones
-- de cuentas por cobrar que no se hablaban y daban números distintos.
--
-- Aquí se queda una sola: la derivada. La columna sobrevive con otro nombre
-- y otro papel — el saldo con el que un cliente ENTRA al sistema, que es un
-- dato que nadie puede calcular y que se perdía si se borraba.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:046-client-opening-balance
--comment: La columna pasa a ser el saldo inicial, lo único que no es derivable.
ALTER TABLE clients RENAME COLUMN balance TO opening_balance;
ALTER TABLE clients ALTER COLUMN opening_balance SET DEFAULT 0;
COMMENT ON COLUMN clients.opening_balance IS
    'Saldo con el que el cliente entra al sistema (migración de otro sistema, '
    'deuda previa). NO es el saldo actual: ese se deriva en v_client_balance.';
--rollback ALTER TABLE clients RENAME COLUMN opening_balance TO balance;

--changeset erp_maya:046-clean-residual-balances
--comment: Los valores actuales son residuo del contador roto, no saldos iniciales.
-- Se verificó que ambos se explican por completo con las restas de abonos:
-- cliente 1 = 0 - 40, cliente 2 = 0 - 1500. Ninguno era un saldo de apertura,
-- así que ponerlos en cero no pierde información; dejarlos la duplicaría,
-- porque esos mismos abonos ya los cuenta la vista.
UPDATE clients SET opening_balance = 0;
ALTER TABLE clients ALTER COLUMN opening_balance SET NOT NULL;
--rollback SELECT 1;

--changeset erp_maya:046-client-balance-view
--comment: La definición única de cuentas por cobrar.
-- Suma signed_total y no total: una nota de crédito contra una venta a
-- crédito baja el saldo sola, sin que nadie tenga que acordarse del signo.
CREATE VIEW v_client_balance AS
SELECT c.id                                AS client_id,
       c.company_id                        AS company_id,
       c.opening_balance
         + COALESCE(v.charged, 0)
         - COALESCE(p.paid, 0)             AS balance,
       COALESCE(v.charged, 0)              AS charged,
       COALESCE(p.paid, 0)                 AS paid
  FROM clients c
  LEFT JOIN (
        -- Lo facturado a crédito. El método de pago es la definición que ya
        -- usaba CreditSaleRepository; cuando la etapa 3 traiga la venta a
        -- crédito de verdad, se cambia aquí y en un solo lugar más.
        SELECT client_id, SUM(signed_total) AS charged
          FROM sales
         WHERE client_id IS NOT NULL
           AND LOWER(COALESCE(payment_method, '')) LIKE '%cred%'
         GROUP BY client_id
  ) v ON v.client_id = c.id
  LEFT JOIN (
        -- TODOS los abonos, aplicados a un documento o a cuenta. La antigüedad
        -- de saldos solo veía los aplicados, y por eso un anticipo no bajaba
        -- nada ahí mientras aquí sí.
        SELECT client_id, SUM(amount) AS paid
          FROM payments
         GROUP BY client_id
  ) p ON p.client_id = c.id;
COMMENT ON VIEW v_client_balance IS
    'Saldo por cobrar de cada cliente. Fuente única: ClientService y la '
    'antigüedad de saldos leen de aquí, nadie mantiene un contador aparte.';
--rollback DROP VIEW v_client_balance;
