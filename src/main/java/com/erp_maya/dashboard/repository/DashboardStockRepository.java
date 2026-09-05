package com.erp_maya.dashboard.repository;

import com.erp_maya.inventory.domain.ProductStock;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Agregaciones del dashboard sobre existencias. */
@Repository
public interface DashboardStockRepository extends JpaRepository<ProductStock, Long> {

    /** [ productId, sku, name, SUM(qty), minStock ] de productos con existencia por debajo del mínimo. */
    @Query("SELECT p.id, p.sku, p.name, SUM(ps.quantity), COALESCE(p.minStock, 0) "
            + "FROM ProductStock ps JOIN ps.product p "
            + "WHERE ps.companyId = :companyId "
            + "GROUP BY p.id, p.sku, p.name, p.minStock "
            + "HAVING SUM(ps.quantity) < COALESCE(p.minStock, 0) "
            + "ORDER BY SUM(ps.quantity)")
    List<Object[]> lowStock(Long companyId);
}
