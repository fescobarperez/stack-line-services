package com.erp_maya.accounting.repository;

import com.erp_maya.accounting.domain.PostingRule;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostingRuleRepository extends JpaRepository<PostingRule, Long> {

    List<PostingRule> findByCompanyId(Long companyId);

    Optional<PostingRule> findByCompanyIdAndUseCase(Long companyId, String useCase);

    boolean existsByTemplateId(Long templateId);
}
