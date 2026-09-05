package com.erp_maya.bank.repository;

import com.erp_maya.bank.domain.BankMovement;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface BankMovementRepository extends JpaRepository<BankMovement, Long> {

    List<BankMovement> findByCompanyIdAndBankAccountIdOrderByMovementDateDesc(Long companyId, Long bankAccountId);
}
