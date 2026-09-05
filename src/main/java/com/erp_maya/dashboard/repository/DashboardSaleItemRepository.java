package com.erp_maya.dashboard.repository;

import com.erp_maya.pos.domain.SaleItem;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
import java.util.List;

/** Agregaciones del dashboard sobre líneas de venta. */
@Repository
public interface DashboardSaleItemRepository extends JpaRepository<SaleItem, Long> {

    /** [ productId, sku, name, SUM(qty), SUM(lineTotal) ] top productos desde una fecha. */
    @Query("SELECT si.product.id, si.product.sku, si.product.name, SUM(si.quantity), SUM(si.lineTotal) FROM SaleItem si WHERE si.companyId = :companyId AND si.sale.saleDate >= :from GROUP BY si.product.id, si.product.sku, si.product.name ORDER BY SUM(si.lineTotal) DESC")
    List<Object[]> topProductsSince(Long companyId, Instant from, Pageable pageable);

    /** [ categoryName, SUM(lineTotal) ] ventas por categoría desde una fecha. */
    @Query("SELECT si.product.category.name, SUM(si.lineTotal) FROM SaleItem si WHERE si.companyId = :companyId AND si.sale.saleDate >= :from GROUP BY si.product.category.name ORDER BY SUM(si.lineTotal) DESC")
    List<Object[]> categorySalesSince(Long companyId, Instant from);

    /** [ categoryName, SUM(lineTotal), SUM(qty*cost) ] ventas y costo por categoría (margen). */
    @Query("SELECT si.product.category.name, SUM(si.lineTotal), SUM(si.quantity * COALESCE(si.product.cost, 0)) FROM SaleItem si WHERE si.companyId = :companyId AND si.sale.saleDate >= :from GROUP BY si.product.category.name ORDER BY SUM(si.lineTotal) DESC")
    List<Object[]> categoryMarginSince(Long companyId, Instant from);
}
