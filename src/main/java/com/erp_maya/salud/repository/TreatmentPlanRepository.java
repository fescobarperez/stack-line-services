package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.TreatmentPlan;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, Long> {

    Optional<TreatmentPlan> findByIdAndCompanyId(Long id, Long companyId);

    List<TreatmentPlan> findByPatientIdAndCompanyIdOrderByCreatedAtDesc(Long patientId, Long companyId);
}

