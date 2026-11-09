package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.ClinicalCase;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClinicalCaseRepository extends JpaRepository<ClinicalCase, Long> {

    Optional<ClinicalCase> findByIdAndCompanyId(Long id, Long companyId);

    List<ClinicalCase> findByPatientIdAndCompanyIdOrderByOpenedAtDesc(Long patientId, Long companyId);
}
