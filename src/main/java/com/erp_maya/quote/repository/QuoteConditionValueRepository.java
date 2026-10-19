package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuoteConditionValue;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface QuoteConditionValueRepository extends JpaRepository<QuoteConditionValue, Long> {

    List<QuoteConditionValue> findByCompanyIdAndQuoteId(Long companyId, Long quoteId);
}
