package com.erp_maya.loyalty.repository;

import com.erp_maya.loyalty.domain.LoyaltyMovement;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface LoyaltyMovementRepository extends JpaRepository<LoyaltyMovement, Long> {

    List<LoyaltyMovement> findByCompanyIdAndAccountIdOrderByIdDesc(Long companyId, Long accountId);
}
