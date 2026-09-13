--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Correlativos reales para cotizaciones y proyectos.
--
-- Hasta aquí la cotización se numeraba con "COT-" + epochMillis y el
-- proyecto con PRY-%04d sobre COUNT(*)+1. Ninguno de los dos es un
-- correlativo: el primero no es legible ni ordenable, y el segundo repite
-- número en cuanto se borra un proyecto —y projects.code es UNIQUE por
-- empresa, así que ahí revienta el insert.
--
-- Duplicar proyectos ejercita los dos caminos de golpe, por eso se
-- arreglan ahora y no cuando alguien tropiece en producción.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:063-seed-quote-project-sequences
--comment: Series COT, RFQ y PRY por empresa, arrancando después de lo ya emitido.
-- next_number sale del máximo que YA use el formato nuevo: los números
-- viejos (epochMillis, PRY-MIG-…) no encajan en el patrón y no estorban,
-- pero un documento capturado a mano como COT-000007 sí, y saltárselo
-- chocaría contra uq_quotes_doc.
INSERT INTO document_sequences (company_id, doc_type, series, prefix, padding, next_number)
SELECT c.id, 'COT', 'A', 'COT-', 6,
       COALESCE((SELECT MAX(SUBSTRING(q.doc_number FROM 5)::bigint)
                   FROM quotes q
                  WHERE q.company_id = c.id
                    AND q.doc_number ~ '^COT-[0-9]{6}$'), 0) + 1
  FROM companies c
 WHERE NOT EXISTS (SELECT 1 FROM document_sequences d
                    WHERE d.company_id = c.id AND d.doc_type = 'COT' AND d.series = 'A');

INSERT INTO document_sequences (company_id, doc_type, series, prefix, padding, next_number)
SELECT c.id, 'RFQ', 'A', 'RFQ-', 6,
       COALESCE((SELECT MAX(SUBSTRING(q.doc_number FROM 5)::bigint)
                   FROM quotes q
                  WHERE q.company_id = c.id
                    AND q.doc_number ~ '^RFQ-[0-9]{6}$'), 0) + 1
  FROM companies c
 WHERE NOT EXISTS (SELECT 1 FROM document_sequences d
                    WHERE d.company_id = c.id AND d.doc_type = 'RFQ' AND d.series = 'A');

-- Padding 4 y no 6: los proyectos existentes son PRY-0001 y cambiar el
-- ancho partiría la numeración en dos formatos.
INSERT INTO document_sequences (company_id, doc_type, series, prefix, padding, next_number)
SELECT c.id, 'PRY', 'A', 'PRY-', 4,
       COALESCE((SELECT MAX(SUBSTRING(p.code FROM 5)::bigint)
                   FROM projects p
                  WHERE p.company_id = c.id
                    AND p.code ~ '^PRY-[0-9]{4}$'), 0) + 1
  FROM companies c
 WHERE NOT EXISTS (SELECT 1 FROM document_sequences d
                    WHERE d.company_id = c.id AND d.doc_type = 'PRY' AND d.series = 'A');
--rollback DELETE FROM document_sequences WHERE doc_type IN ('COT', 'RFQ', 'PRY');
