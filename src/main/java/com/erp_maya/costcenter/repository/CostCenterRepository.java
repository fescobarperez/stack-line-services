package com.erp_maya.costcenter.repository;

import com.erp_maya.costcenter.domain.CostCenter;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenter, Long> {

    List<CostCenter> findByCompanyIdOrderByCode(Long companyId);

    Optional<CostCenter> findByIdAndCompanyId(Long id, Long companyId);
}
