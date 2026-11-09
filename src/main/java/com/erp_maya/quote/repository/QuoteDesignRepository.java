package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuoteDesign;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Repository
public interface QuoteDesignRepository extends JpaRepository<QuoteDesign, Long> {

    Optional<QuoteDesign> findByQuoteIdAndCompanyId(Long quoteId, Long companyId);
}
