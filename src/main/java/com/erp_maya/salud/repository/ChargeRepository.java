package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Charge;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeRepository extends JpaRepository<Charge, Long> {

    Optional<Charge> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Charge> findByAppointmentIdAndStatus(Long appointmentId, String status);

    List<Charge> findByPatientIdAndCompanyIdOrderByCreatedAtDesc(Long patientId, Long companyId);

    List<Charge> findByCompanyIdAndStatusAndCreatedAtGreaterThanEqualsAndCreatedAtLessThan(
            Long companyId, String status, Instant desde, Instant hasta);
}

