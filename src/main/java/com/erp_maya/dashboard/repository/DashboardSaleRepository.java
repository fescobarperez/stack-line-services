package com.erp_maya.dashboard.repository;

import com.erp_maya.pos.domain.Sale;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
import java.util.List;

/** Agregaciones del dashboard sobre ventas. */
@Repository
public interface DashboardSaleRepository extends JpaRepository<Sale, Long> {

    /** [ SUM(total), COUNT ] de las ventas desde una fecha. */
    @Query("SELECT COALESCE(SUM(s.signedTotal), 0), COUNT(s) FROM Sale s WHERE s.companyId = :companyId AND s.saleDate >= :from")
    List<Object[]> totalsSince(Long companyId, Instant from);

    /** [ saleDate, total ] de las ventas desde una fecha (para la serie diaria). */
    @Query("SELECT s.saleDate, s.signedTotal FROM Sale s WHERE s.companyId = :companyId AND s.saleDate >= :from ORDER BY s.saleDate")
    List<Object[]> amountsSince(Long companyId, Instant from);

    /** [ branchId, branchName, SUM(total) ] agrupado por sucursal desde una fecha. */
    @Query("SELECT s.branch.id, s.branch.name, SUM(s.signedTotal) FROM Sale s WHERE s.companyId = :companyId AND s.saleDate >= :from GROUP BY s.branch.id, s.branch.name ORDER BY SUM(s.signedTotal) DESC")
    List<Object[]> branchSalesSince(Long companyId, Instant from);

    /** [ docNumber, saleDate, branchName, paymentMethod, total ] de las ventas más recientes. */
    @Query("SELECT s.docNumber, s.saleDate, s.branch.name, s.paymentMethod, s.signedTotal FROM Sale s WHERE s.companyId = :companyId ORDER BY s.saleDate DESC")
    List<Object[]> recentTickets(Long companyId, Pageable pageable);

    /** [ branchId, branchName, COUNT, SUM(total) ] por sucursal desde una fecha (reporte de ventas). */
    @Query("SELECT s.branch.id, s.branch.name, COUNT(s), SUM(s.signedTotal) FROM Sale s WHERE s.companyId = :companyId AND s.saleDate >= :from GROUP BY s.branch.id, s.branch.name ORDER BY SUM(s.signedTotal) DESC")
    List<Object[]> branchSalesWithCountSince(Long companyId, Instant from);

    /** [ paymentMethod, SUM(total) ] por método de pago desde una fecha. */
    @Query("SELECT s.paymentMethod, SUM(s.signedTotal) FROM Sale s WHERE s.companyId = :companyId AND s.saleDate >= :from GROUP BY s.paymentMethod ORDER BY SUM(s.signedTotal) DESC")
    List<Object[]> paymentBreakdownSince(Long companyId, Instant from);
}
