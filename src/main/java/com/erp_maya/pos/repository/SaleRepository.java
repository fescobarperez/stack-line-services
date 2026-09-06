package com.erp_maya.pos.repository;

import com.erp_maya.pos.domain.Sale;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    Page<Sale> findByCompanyIdOrderBySaleDateDesc(Long companyId, Pageable pageable);

    Optional<Sale> findByIdAndCompanyId(Long id, Long companyId);

    /** Ventas emitidas contra un proyecto: base del facturado. */
    java.util.List<Sale> findByCompanyIdAndProjectId(Long companyId, Long projectId);
}
