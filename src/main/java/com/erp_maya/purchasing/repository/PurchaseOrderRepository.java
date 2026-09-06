package com.erp_maya.purchasing.repository;

import com.erp_maya.purchasing.domain.PurchaseOrder;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findByCompanyIdOrderByOrderDateDesc(Long companyId, Pageable pageable);

    Optional<PurchaseOrder> findByIdAndCompanyId(Long id, Long companyId);

    /** Órdenes imputadas a un proyecto: base del costo comprometido. */
    java.util.List<PurchaseOrder> findByCompanyIdAndProjectId(Long companyId, Long projectId);
}
