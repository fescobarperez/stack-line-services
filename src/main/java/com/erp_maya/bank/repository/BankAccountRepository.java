package com.erp_maya.bank.repository;

import com.erp_maya.bank.domain.BankAccount;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByCompanyId(Long companyId);

    Optional<BankAccount> findByIdAndCompanyId(Long id, Long companyId);
}
