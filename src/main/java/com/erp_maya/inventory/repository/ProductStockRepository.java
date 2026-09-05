package com.erp_maya.inventory.repository;

import com.erp_maya.inventory.domain.ProductStock;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    List<ProductStock> findByCompanyId(Long companyId);

    List<ProductStock> findByCompanyIdAndBranchId(Long companyId, Long branchId);

    List<ProductStock> findByCompanyIdAndProductId(Long companyId, Long productId);

    Optional<ProductStock> findByCompanyIdAndProductIdAndBranchIdAndBatch(
            Long companyId, Long productId, Long branchId, String batch);
}
