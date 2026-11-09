package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.EncounterDiagnosis;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lista hija de la consulta: se reemplaza completa mientras es borrador. */
@Repository
public interface EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, Long> {

    List<EncounterDiagnosis> findByEncounterIdOrderByPosition(Long encounterId);

    void deleteByEncounterId(Long encounterId);
}
