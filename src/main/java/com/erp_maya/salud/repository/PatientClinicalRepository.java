package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.PatientClinical;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Repository
public interface PatientClinicalRepository extends JpaRepository<PatientClinical, Long> {

    Optional<PatientClinical> findByPatientIdAndCompanyId(Long patientId, Long companyId);
}
