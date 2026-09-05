package com.erp_maya.inventory.repository;

import com.erp_maya.inventory.domain.StockMovement;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    Page<StockMovement> findByCompanyIdAndProductIdOrderByCreatedAtDesc(
            Long companyId, Long productId, Pageable pageable);
}
