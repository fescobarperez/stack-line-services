package com.erp_maya.nav.repository;

import com.erp_maya.pos.domain.Sale;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

/** Conteos para los contadores del menú. Solo lectura, una consulta por opción. */
@Repository
public interface NavCounterRepository extends JpaRepository<Sale, Long> {

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.companyId = :companyId "
            + "AND s.status NOT IN ('refunded', 'cancelled') "
            + "AND NOT EXISTS (SELECT f.id FROM FelDocument f WHERE f.sale = s AND f.status = 'autorizado')")
    long ventasSinFel(Long companyId);

    @Query("SELECT COUNT(n) FROM CreditNote n WHERE n.companyId = :companyId "
            + "AND (n.felStatus IS NULL OR n.felStatus <> 'autorizada')")
    long notasSinFel(Long companyId);

    @Query("SELECT COUNT(o) FROM PurchaseOrder o WHERE o.companyId = :companyId "
            + "AND o.status IN ('pending', 'partial')")
    long ordenesAbiertas(Long companyId);

    @Query("SELECT COUNT(t) FROM Transfer t WHERE t.companyId = :companyId "
            + "AND t.status IN ('draft', 'in_transit')")
    long trasladosAbiertos(Long companyId);

    @Query("SELECT COUNT(q) FROM Quote q WHERE q.companyId = :companyId "
            + "AND (q.status IN ('prospecto', 'abierta') "
            + "OR EXISTS (SELECT c.id FROM QuoteChangeRequest c WHERE c.quoteId = q.id "
            + "AND c.companyId = :companyId AND c.status = 'pendiente'))")
    long cotizacionesParaAsesor(Long companyId);

    @Query("SELECT COUNT(i) FROM PurchaseInvoice i WHERE i.companyId = :companyId "
            + "AND i.status <> 'paid' AND i.dueDate IS NOT NULL AND i.dueDate < :today")
    long facturasProveedorVencidas(Long companyId, LocalDate today);
}
