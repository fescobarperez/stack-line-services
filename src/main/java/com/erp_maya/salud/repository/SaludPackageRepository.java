package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.SaludPackage;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaludPackageRepository extends JpaRepository<SaludPackage, Long> {

    Optional<SaludPackage> findByIdAndCompanyId(Long id, Long companyId);

    List<SaludPackage> findByCompanyIdOrderByName(Long companyId);
}

