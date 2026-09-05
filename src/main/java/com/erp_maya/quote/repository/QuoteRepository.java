package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.Quote;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {

    Page<Quote> findByCompanyIdOrderByQuoteDateDesc(Long companyId, Pageable pageable);

    Page<Quote> findByCompanyIdAndPartyTypeOrderByQuoteDateDesc(Long companyId, String partyType, Pageable pageable);

    Optional<Quote> findByIdAndCompanyId(Long id, Long companyId);
}
