package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.PatientVaccine;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lista hija del paciente: se reemplaza completa al guardar (borrar y volver a insertar). */
@Repository
public interface PatientVaccineRepository extends JpaRepository<PatientVaccine, Long> {

    List<PatientVaccine> findByPatientIdOrderById(Long patientId);

    void deleteByPatientId(Long patientId);
}
