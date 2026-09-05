package com.erp_maya.stockcount.repository;

import com.erp_maya.stockcount.domain.StockCount;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    Page<StockCount> findByCompanyIdOrderByCountDateDesc(Long companyId, Pageable pageable);

    Optional<StockCount> findByIdAndCompanyId(Long id, Long companyId);
}
