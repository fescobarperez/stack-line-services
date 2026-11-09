package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.FollowUp;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    Optional<FollowUp> findByIdAndCompanyId(Long id, Long companyId);

    List<FollowUp> findByPatientIdAndCompanyIdOrderByDueOn(Long patientId, Long companyId);

    List<FollowUp> findByAppointmentId(Long appointmentId);

    List<FollowUp> findByCaseIdAndStatus(Long caseId, String status);
}
