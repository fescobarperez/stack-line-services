package com.erp_maya.platform.repository;

import com.erp_maya.platform.domain.CompanySubscription;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {

    long countByPlanId(Long planId);
}
