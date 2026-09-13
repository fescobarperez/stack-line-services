--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Historial de precios por proveedor · siembra inicial.
--
-- product_supplier_prices existe desde la 057 pero nunca se escribió: no
-- tenía entidad ni código. Ahora ProductService la alimenta en cada alta
-- o cambio de costo, y las relaciones que ya existían necesitan su
-- primer precio para no arrancar el historial en blanco.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:064-backfill-supplier-prices
--comment: Cada relación producto-proveedor estrena su precio vigente.
-- valid_from = created_at de la relación: es lo más cercano a la verdad
-- que hay. El costo anterior a hoy no se puede reconstruir porque nunca
-- se guardó; el historial empieza aquí y de aquí en adelante es exacto.
INSERT INTO product_supplier_prices (company_id, product_supplier_id, unit_cost, valid_from)
SELECT ps.company_id, ps.id, ps.unit_cost, ps.created_at
  FROM product_suppliers ps
 WHERE ps.unit_cost > 0
   AND NOT EXISTS (SELECT 1 FROM product_supplier_prices p
                    WHERE p.product_supplier_id = ps.id);
--rollback DELETE FROM product_supplier_prices;
