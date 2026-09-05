package com.erp_maya.company.repository;

import com.erp_maya.company.domain.Branch;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByCompanyId(Long companyId);

    Optional<Branch> findByIdAndCompanyId(Long id, Long companyId);
}
