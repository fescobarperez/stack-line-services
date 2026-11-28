package com.erp_maya.accounting.repository;

import com.erp_maya.accounting.domain.JournalTemplate;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JournalTemplateRepository extends JpaRepository<JournalTemplate, Long> {

    List<JournalTemplate> findByCompanyId(Long companyId);

    Optional<JournalTemplate> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndUseCase(Long companyId, String useCase);
}
