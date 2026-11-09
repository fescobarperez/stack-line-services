package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.ReminderCandidate;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

/** Las condiciones viven en la vista (091): aquí solo se toma un lote, lo más próximo primero. */
@Repository
public interface ReminderCandidateRepository extends GenericRepository<ReminderCandidate, Long> {

    @Query(value = "SELECT * FROM salud_v_reminders_due ORDER BY starts_at LIMIT :limite", nativeQuery = true)
    List<ReminderCandidate> due(int limite);
}
