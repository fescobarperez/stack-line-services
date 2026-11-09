package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuoteChangeRequest;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface QuoteChangeRequestRepository extends JpaRepository<QuoteChangeRequest, Long> {

    List<QuoteChangeRequest> findByCompanyIdAndQuoteIdOrderByCreatedAtAsc(Long companyId, Long quoteId);

    long countByCompanyIdAndQuoteIdAndStatus(Long companyId, Long quoteId, String status);
}
