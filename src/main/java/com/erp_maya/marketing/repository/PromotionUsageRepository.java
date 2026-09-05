package com.erp_maya.marketing.repository;

import com.erp_maya.marketing.domain.PromotionUsage;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    List<PromotionUsage> findByCompanyId(Long companyId);

    /** [promotionId, usos, ahorroTotal, tickets] agregado por promoción. */
    @Query("SELECT u.promotionId, COUNT(u), COALESCE(SUM(u.amountSaved), 0), COUNT(DISTINCT u.saleId) "
            + "FROM PromotionUsage u WHERE u.companyId = :companyId GROUP BY u.promotionId")
    List<Object[]> aggregateByPromotion(Long companyId);
}
