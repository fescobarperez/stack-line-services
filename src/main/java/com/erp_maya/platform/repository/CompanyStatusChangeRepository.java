package com.erp_maya.platform.repository;

import com.erp_maya.platform.domain.CompanyStatusChange;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface CompanyStatusChangeRepository extends JpaRepository<CompanyStatusChange, Long> {

    List<CompanyStatusChange> findByCompanyIdOrderByCreatedAtAsc(Long companyId);
}
