package com.erp_maya.receivable.repository;

import com.erp_maya.pos.domain.Sale;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Ventas a crédito, base de las cuentas por cobrar (aging). */
@Repository
public interface CreditSaleRepository extends JpaRepository<Sale, Long> {

    /**
     * [ saleId, docNumber, clientId, clientName, saleDate, total, paymentTerms ] de las ventas
     * a crédito, ordenadas por fecha.
     *
     * Antes esto se resolvía con LIKE '%cred%' sobre el método de pago: un
     * espacio de más o una tilde sacaban la venta de cuentas por cobrar. Desde
     * la 048 hay una columna que lo dice.
     */
    @Query("SELECT s.id, s.docNumber, s.client.id, s.client.name, s.saleDate, s.total, s.client.paymentTerms "
            + "FROM Sale s WHERE s.companyId = :companyId AND s.client IS NOT NULL "
            + "AND s.credit = TRUE AND s.status <> 'cancelled' ORDER BY s.saleDate")
    List<Object[]> creditSales(Long companyId);
}
