package com.erp_maya.marketing.repository;

import com.erp_maya.marketing.domain.Promotion;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByCompanyId(Long companyId);

    Optional<Promotion> findByIdAndCompanyId(Long id, Long companyId);
}
