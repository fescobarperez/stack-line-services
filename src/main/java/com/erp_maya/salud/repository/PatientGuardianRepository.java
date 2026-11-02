package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.PatientGuardian;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lista hija del paciente: se reemplaza completa al guardar (borrar y volver a insertar). */
@Repository
public interface PatientGuardianRepository extends JpaRepository<PatientGuardian, Long> {

    List<PatientGuardian> findByPatientIdOrderById(Long patientId);

    void deleteByPatientId(Long patientId);
}
