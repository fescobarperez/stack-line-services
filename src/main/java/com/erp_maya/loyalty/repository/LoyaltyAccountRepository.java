package com.erp_maya.loyalty.repository;

import com.erp_maya.loyalty.domain.LoyaltyAccount;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    List<LoyaltyAccount> findByCompanyId(Long companyId);

    Optional<LoyaltyAccount> findByIdAndCompanyId(Long id, Long companyId);
}
