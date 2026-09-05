package com.erp_maya.bank.repository;

import com.erp_maya.bank.domain.BankReconciliation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, Long> {

    List<BankReconciliation> findByCompanyIdAndBankAccountIdOrderByStatementDateDesc(Long companyId, Long bankAccountId);
}
