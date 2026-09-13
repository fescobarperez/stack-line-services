package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.ChargeCategory;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeCategoryRepository extends JpaRepository<ChargeCategory, Long> {

    List<ChargeCategory> findByCompanyIdOrderBySortOrderAsc(Long companyId);

    List<ChargeCategory> findByCompanyIdAndStatusOrderBySortOrderAsc(Long companyId, String status);

    Optional<ChargeCategory> findByIdAndCompanyId(Long id, Long companyId);

    Optional<ChargeCategory> findByCompanyIdAndCode(Long companyId, String code);
}
