package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuoteCharge;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteChargeRepository extends JpaRepository<QuoteCharge, Long> {
    List<QuoteCharge> findByCompanyIdAndQuoteIdOrderBySortOrderAsc(Long companyId, Long quoteId);
    Optional<QuoteCharge> findByIdAndCompanyId(Long id, Long companyId);
}
