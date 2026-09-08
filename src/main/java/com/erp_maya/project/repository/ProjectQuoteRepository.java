package com.erp_maya.project.repository;

import com.erp_maya.project.domain.ProjectQuote;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectQuoteRepository extends JpaRepository<ProjectQuote, Long> {
    Optional<ProjectQuote> findByCompanyIdAndQuoteId(Long companyId, Long quoteId);
    List<ProjectQuote> findByCompanyIdAndProjectId(Long companyId, Long projectId);
}
