package com.erp_maya.accounting.repository;

import com.erp_maya.accounting.domain.Account;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCompanyIdOrderByCode(Long companyId);

    Optional<Account> findByIdAndCompanyId(Long id, Long companyId);
}
