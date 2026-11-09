package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.PatientConsent;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientConsentRepository extends JpaRepository<PatientConsent, Long> {

    List<PatientConsent> findByPatientIdOrderBySignedAtDesc(Long patientId);

    Optional<PatientConsent> findByIdAndPatientIdAndCompanyId(Long id, Long patientId, Long companyId);
}
