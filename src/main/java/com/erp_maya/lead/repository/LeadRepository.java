package com.erp_maya.lead.repository;

import com.erp_maya.lead.domain.Lead;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Lead> findByCompanyIdAndSourceRef(Long companyId, String sourceRef);

    Page<Lead> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    Page<Lead> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status, Pageable pageable);
}
