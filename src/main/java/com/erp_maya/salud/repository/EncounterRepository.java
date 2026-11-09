package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Encounter;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    Optional<Encounter> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Encounter> findByAppointmentId(Long appointmentId);
}
