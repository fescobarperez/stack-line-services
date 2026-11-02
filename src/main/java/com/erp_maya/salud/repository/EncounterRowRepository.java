package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.EncounterRow;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;
import java.util.Optional;

/** Consultas sobre la vista salud_v_encounters (solo lectura). */
@Repository
public interface EncounterRowRepository extends GenericRepository<EncounterRow, Long> {

    Optional<EncounterRow> findById(Long id);

    List<EncounterRow> findByPatientIdAndCompanyIdOrderByCreatedAtDesc(Long patientId, Long companyId);

    /** La última consulta firmada del caso antes de esta: lo que se compara en el control. */
    @Query(value = "SELECT * FROM salud_v_encounters WHERE case_id = :caseId AND status = 'firmada' "
            + "AND id <> :excluir ORDER BY signed_at DESC LIMIT 1", nativeQuery = true)
    Optional<EncounterRow> previousSigned(Long caseId, long excluir);
}
