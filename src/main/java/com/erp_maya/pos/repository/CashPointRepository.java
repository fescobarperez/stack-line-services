package com.erp_maya.pos.repository;

import com.erp_maya.pos.domain.CashPoint;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashPointRepository extends JpaRepository<CashPoint, Long> {

    List<CashPoint> findByCompanyIdOrderByCodeAsc(Long companyId);

    List<CashPoint> findByCompanyIdAndBranchIdOrderByCodeAsc(Long companyId, Long branchId);

    Optional<CashPoint> findByIdAndCompanyId(Long id, Long companyId);

    Optional<CashPoint> findByCompanyIdAndBranchIdAndCode(Long companyId, Long branchId, String code);
}
