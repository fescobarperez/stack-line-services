--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Tipificación del documento de venta.
--
-- Hasta ahora `sales` era implícitamente una factura: SaleService mandaba
-- "FACT" y serie "A" quemados al certificador. El tipo sí existía, pero en
-- `fel_documents.dte_type` — es decir, en el comprobante de certificación y
-- no en el documento comercial. Eso deja la lógica de negocio (signo, IVA,
-- inventario, CxC) dependiendo de si el DTE llegó a certificarse o no.
--
-- Aquí el tipo sube al documento. `fel_documents` sigue guardando el suyo
-- como evidencia fiscal, pero el que manda es este.
--
-- Lo que NO entra: el recibo de caja que soporta un abono contra una factura
-- ya emitida. No es DTE, no lleva renglones ni IVA y no es ingreso — vive en
-- `payments`. Meterlo aquí obligaría a filtrarlo en toda consulta que
-- signifique "cuánto vendí", y la primera que se olvidara inflaría las
-- ventas. Ojo con el falso amigo: SAT sí tiene un DTE llamado RECI, pero es
-- otra cosa y cabe en esta misma tabla el día que se ocupe.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:045-sale-doc-type
--comment: Tipo de documento. Lo existente es factura: no cambia nada de lo que ya opera.
ALTER TABLE sales ADD COLUMN doc_type VARCHAR(10) NOT NULL DEFAULT 'FACT';
CREATE INDEX ix_sales_doc_type ON sales (company_id, doc_type);
-- Sin CHECK, igual que stock_movements.movement_type en la 039: el catálogo lo
-- define SAT y crece (regímenes, agropecuario, factura especial). Un CHECK
-- obligaría a una migración por cada tipo nuevo, que es justo lo que se quiere
-- evitar. Se documenta el vocabulario y se valida en el servicio.
COMMENT ON COLUMN sales.doc_type IS
    'DTE de SAT. FACT factura | FCAM cambiaria | FPEQ pequeño contribuyente | '
    'FESP especial | NCRE nota de crédito | NDEB nota de débito | NABN nota de '
    'abono | RECI recibo. NO confundir RECI con el recibo de caja de payments.';
--rollback DROP INDEX ix_sales_doc_type; ALTER TABLE sales DROP COLUMN doc_type;

--changeset erp_maya:045-sale-series
--comment: Serie del documento, hoy quemada a "A" en SaleService.
ALTER TABLE sales ADD COLUMN series VARCHAR(12);
--rollback ALTER TABLE sales DROP COLUMN series;

--changeset erp_maya:045-sale-related
--comment: Documento al que modifica. SAT exige que toda NC/ND referencie el DTE original.
ALTER TABLE sales ADD COLUMN related_sale_id BIGINT REFERENCES sales (id);
ALTER TABLE sales ADD COLUMN reason VARCHAR(200);
CREATE INDEX ix_sales_related ON sales (related_sale_id) WHERE related_sale_id IS NOT NULL;
--rollback DROP INDEX ix_sales_related; ALTER TABLE sales DROP COLUMN reason; ALTER TABLE sales DROP COLUMN related_sale_id;

--changeset erp_maya:045-sale-signed-total
--comment: Total con signo por tipo. Evita repetir el CASE en cada agregación.
-- Se guarda SIEMPRE en positivo: FEL envía montos positivos también en una
-- NCRE, así que almacenar negativo pelearía con el certificador y con el
-- documento impreso. El signo es cosa de quien suma, no de quien guarda.
-- Columna generada para que no dependa de que nadie olvide el CASE.
ALTER TABLE sales ADD COLUMN signed_total NUMERIC(14,2)
    GENERATED ALWAYS AS (CASE WHEN doc_type IN ('NCRE', 'NABN') THEN -total ELSE total END) STORED;
COMMENT ON COLUMN sales.signed_total IS
    'total con signo contable. Toda agregación de ventas suma esta, no total.';
--rollback ALTER TABLE sales DROP COLUMN signed_total;
