package com.erp_maya.project.repository;

import com.erp_maya.project.domain.Project;
import com.erp_maya.project.domain.ProjectCost;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public class ProjectRepositories {

    @Repository
    public interface Projects extends JpaRepository<Project, Long> {
        List<Project> findByCompanyIdOrderByIdDesc(Long companyId);
        Optional<Project> findByIdAndCompanyId(Long id, Long companyId);
        Optional<Project> findByCompanyIdAndQuoteId(Long companyId, Long quoteId);
        Optional<Project> findByCompanyIdAndClientIdAndName(Long companyId, Long clientId, String name);
        Optional<Project> findByCompanyIdAndCode(Long companyId, String code);
        long countByCompanyId(Long companyId);
    }

    @Repository
    public interface Costs extends JpaRepository<ProjectCost, Long> {
        List<ProjectCost> findByProjectIdOrderByCostDateDesc(Long projectId);
        List<ProjectCost> findByCompanyIdAndProjectId(Long companyId, Long projectId);
        Optional<ProjectCost> findByIdAndCompanyId(Long id, Long companyId);
    }
}
