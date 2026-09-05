package com.erp_maya.notification.repository;

import com.erp_maya.purchasing.domain.PurchaseOrder;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Órdenes de compra pendientes (para notificaciones). */
@Repository
public interface NotifPurchaseRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("SELECT po.id, po.docNumber, po.supplier.name FROM PurchaseOrder po "
            + "WHERE po.companyId = :companyId AND po.status = 'pending' ORDER BY po.orderDate")
    List<Object[]> pending(Long companyId);
}
