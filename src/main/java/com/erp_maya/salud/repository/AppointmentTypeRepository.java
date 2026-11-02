package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.AppointmentType;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentTypeRepository extends JpaRepository<AppointmentType, Long> {

    List<AppointmentType> findByCompanyIdOrderByName(Long companyId);

    Optional<AppointmentType> findByIdAndCompanyId(Long id, Long companyId);

    Optional<AppointmentType> findByCompanyIdAndNameIlike(Long companyId, String name);
}
