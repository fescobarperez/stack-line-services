package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.FollowUpRow;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Bandeja de controles (vista salud_v_follow_ups). professionalId 0 = todos. */
@Repository
public interface FollowUpRowRepository extends GenericRepository<FollowUpRow, Long> {

    Optional<FollowUpRow> findByIdAndCompanyId(Long id, Long companyId);

    @Query(value = "SELECT * FROM salud_v_follow_ups WHERE company_id = :companyId AND status = 'pendiente' "
            + "AND case_status = 'abierto' AND due_on <= :hasta "
            + "AND (:professionalId = 0 OR professional_id = :professionalId) "
            + "ORDER BY due_on, id LIMIT :limite",
           nativeQuery = true)
    List<FollowUpRow> pending(Long companyId, LocalDate hasta, long professionalId, int limite);
}

