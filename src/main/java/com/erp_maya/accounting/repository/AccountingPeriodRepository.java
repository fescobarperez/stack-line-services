package com.erp_maya.accounting.repository;

import com.erp_maya.accounting.domain.AccountingPeriod;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    List<AccountingPeriod> findByCompanyIdOrderByStartDateDesc(Long companyId);

    Optional<AccountingPeriod> findByIdAndCompanyId(Long id, Long companyId);
}
