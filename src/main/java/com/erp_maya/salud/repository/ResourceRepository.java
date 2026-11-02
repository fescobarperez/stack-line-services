package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Resource;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findByCompanyIdOrderByName(Long companyId);

    List<Resource> findByCompanyIdAndActiveOrderByName(Long companyId, boolean active);

    Optional<Resource> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Resource> findByCompanyIdAndNameIlike(Long companyId, String name);
}
