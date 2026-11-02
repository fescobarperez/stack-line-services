package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.ControlReminderCandidate;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import java.util.List;

/** Las condiciones viven en la vista (093): aquí solo se toma un lote, lo más urgente primero. */
@Repository
public interface ControlReminderCandidateRepository extends GenericRepository<ControlReminderCandidate, Long> {

    @Query(value = "SELECT * FROM salud_v_control_reminders_due ORDER BY due_on, id LIMIT :limite", nativeQuery = true)
    List<ControlReminderCandidate> due(int limite);
}

