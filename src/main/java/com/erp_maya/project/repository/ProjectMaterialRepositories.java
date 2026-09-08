package com.erp_maya.project.repository;

import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.domain.ProjectMaterialGroup;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public class ProjectMaterialRepositories {

    @Repository
    public interface Groups extends JpaRepository<ProjectMaterialGroup, Long> {
        List<ProjectMaterialGroup> findByCompanyIdAndProjectIdOrderBySortOrderAsc(Long companyId, Long projectId);
        Optional<ProjectMaterialGroup> findByIdAndCompanyId(Long id, Long companyId);
    }

    @Repository
    public interface Materials extends JpaRepository<ProjectMaterial, Long> {
        List<ProjectMaterial> findByCompanyIdAndProjectIdOrderByIdAsc(Long companyId, Long projectId);
        Optional<ProjectMaterial> findByIdAndCompanyId(Long id, Long companyId);
        // Pool disponible del proyecto: materiales aún sin cotización.
        List<ProjectMaterial> findByCompanyIdAndProjectIdAndQuoteIdIsNullOrderByIdAsc(Long companyId, Long projectId);
        // Materiales asignados a una cotización (para costo de línea y liberación).
        List<ProjectMaterial> findByCompanyIdAndQuoteId(Long companyId, Long quoteId);
        List<ProjectMaterial> findByCompanyIdAndProjectIdAndIdIn(Long companyId, Long projectId, List<Long> ids);
    }
}
