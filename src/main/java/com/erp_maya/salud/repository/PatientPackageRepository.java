package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.PatientPackage;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientPackageRepository extends JpaRepository<PatientPackage, Long> {

    Optional<PatientPackage> findByIdAndCompanyId(Long id, Long companyId);

    List<PatientPackage> findByPatientIdAndCompanyIdOrderByPurchasedAtDesc(Long patientId, Long companyId);

    List<PatientPackage> findByPatientIdAndCompanyIdAndStatusOrderByPurchasedAt(Long patientId, Long companyId, String status);

    /** Vendidos en el periodo (reportes). */
    List<PatientPackage> findByCompanyIdAndPurchasedAtGreaterThanEqualsAndPurchasedAtLessThan(Long companyId, Instant desde, Instant hasta);
}

