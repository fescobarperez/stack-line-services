--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Recibos de caja y correlativos de documento.
--
-- El recibo es el papel que el cliente recibe por cada abono cuando la
-- factura ya se emitió y quedó en cuentas por cobrar. Es interno y NO
-- fiscal: el hecho generador ya lo cubrió la factura, así que aquí no hay
-- IVA ni DTE. No confundir con el RECI del catálogo de SAT, que es otra
-- cosa y cabe en `sales` con su doc_type.
--
-- Va como columnas de `payments` y no como tabla aparte: un recibo sin
-- cobro no existe, y una tabla 1:1 solo duplicaría el concepto.
--
-- El correlativo se saca a su propia tabla porque hasta ahora no había
-- ninguno: SaleService ponía "T-" + epochMillis cuando no le mandaban
-- número. Sirve para recibo, factura y las notas que vengan.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:050-document-sequences
--comment: Correlativos por empresa, tipo de documento y serie.
CREATE TABLE document_sequences (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES companies (id),
    doc_type     VARCHAR(20)  NOT NULL,
    series       VARCHAR(12)  NOT NULL DEFAULT 'A',
    prefix       VARCHAR(12)  NOT NULL DEFAULT '',
    next_number  BIGINT       NOT NULL DEFAULT 1,
    padding      SMALLINT     NOT NULL DEFAULT 6,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_doc_sequences UNIQUE (company_id, doc_type, series),
    CONSTRAINT ck_doc_sequences_next CHECK (next_number > 0),
    CONSTRAINT ck_doc_sequences_padding CHECK (padding BETWEEN 0 AND 12)
);
COMMENT ON TABLE document_sequences IS
    'Numeración correlativa. Se reserva con SELECT … FOR UPDATE: dos cajeros '
    'cobrando a la vez no pueden sacar el mismo número.';
--rollback DROP TABLE document_sequences;

--changeset erp_maya:050-seed-sequences
--comment: Serie inicial de recibos y facturas para cada empresa.
INSERT INTO document_sequences (company_id, doc_type, series, prefix, next_number)
SELECT c.id, 'RECIBO', 'A', 'REC-', 1 FROM companies c
WHERE NOT EXISTS (SELECT 1 FROM document_sequences d
                   WHERE d.company_id = c.id AND d.doc_type = 'RECIBO' AND d.series = 'A');
INSERT INTO document_sequences (company_id, doc_type, series, prefix, next_number)
SELECT c.id, 'FACT', 'A', 'FAC-', 1 FROM companies c
WHERE NOT EXISTS (SELECT 1 FROM document_sequences d
                   WHERE d.company_id = c.id AND d.doc_type = 'FACT' AND d.series = 'A');
--rollback DELETE FROM document_sequences;

--changeset erp_maya:050-payment-receipt
--comment: Número de recibo del abono, y cuándo se imprimió.
ALTER TABLE payments ADD COLUMN receipt_number     VARCHAR(40);
ALTER TABLE payments ADD COLUMN receipt_printed_at TIMESTAMPTZ;
-- Único de verdad: si dos cobros simultáneos sacaran el mismo correlativo,
-- que reviente aquí y no que salgan dos recibos con el mismo número.
CREATE UNIQUE INDEX uq_payments_receipt ON payments (company_id, receipt_number)
    WHERE receipt_number IS NOT NULL;
--rollback DROP INDEX uq_payments_receipt; ALTER TABLE payments DROP COLUMN receipt_printed_at; ALTER TABLE payments DROP COLUMN receipt_number;

--changeset erp_maya:050-backfill-receipts
--comment: Los abonos que ya existían también necesitan su número.
-- Se numeran por fecha para que el correlativo respete el orden en que
-- ocurrieron, y la secuencia arranca después del último asignado.
WITH numerados AS (
    SELECT id, company_id,
           ROW_NUMBER() OVER (PARTITION BY company_id ORDER BY payment_date, id) AS n
      FROM payments
)
UPDATE payments p
   SET receipt_number = 'REC-' || LPAD(numerados.n::text, 6, '0')
  FROM numerados
 WHERE numerados.id = p.id;
UPDATE document_sequences d
   SET next_number = COALESCE((SELECT COUNT(*) FROM payments p WHERE p.company_id = d.company_id), 0) + 1
 WHERE d.doc_type = 'RECIBO';
--rollback UPDATE payments SET receipt_number = NULL;
