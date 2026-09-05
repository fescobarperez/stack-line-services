package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuoteHistory;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface QuoteHistoryRepository extends JpaRepository<QuoteHistory, Long> {

    List<QuoteHistory> findByQuoteIdOrderByCreatedAtAsc(Long quoteId);
}
