package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuotePaymentTerm;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuotePaymentTermRepository extends JpaRepository<QuotePaymentTerm, Long> {
    List<QuotePaymentTerm> findByCompanyIdAndQuoteIdOrderBySequenceAsc(Long companyId, Long quoteId);
    Optional<QuotePaymentTerm> findByIdAndCompanyId(Long id, Long companyId);
}
