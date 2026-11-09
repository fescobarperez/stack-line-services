package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.EmergencyContact;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lista hija del paciente: se reemplaza completa al guardar (borrar y volver a insertar). */
@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findByPatientIdOrderBySortOrder(Long patientId);

    void deleteByPatientId(Long patientId);
}
