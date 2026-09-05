package com.erp_maya.payroll.repository;

import com.erp_maya.payroll.domain.PayrollPeriod;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {

    @Query("SELECT p FROM PayrollPeriod p WHERE p.companyId = :companyId ORDER BY p.year DESC, p.month DESC")
    List<PayrollPeriod> findByCompanyIdOrderByYearDescMonthDesc(Long companyId);

    Optional<PayrollPeriod> findByIdAndCompanyId(Long id, Long companyId);

    Optional<PayrollPeriod> findByCompanyIdAndPeriodCode(Long companyId, String periodCode);
}
