package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.AppointmentRow;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Agenda sobre la vista salud_v_appointments. Filtros vacíos: 0 para ids y
 * '' para el estado (no null, por el tipo de los parámetros nativos).
 */
@Repository
public interface AppointmentRowRepository extends GenericRepository<AppointmentRow, Long> {

    Optional<AppointmentRow> findByIdAndCompanyId(Long id, Long companyId);

    @Query(value = "SELECT * FROM salud_v_appointments WHERE company_id = :companyId "
            + "AND starts_at < :hasta AND ends_at > :desde "
            + "AND (:professionalId = 0 OR professional_id = :professionalId) "
            + "AND (:resourceId = 0 OR resource_id = :resourceId) "
            + "AND (:patientId = 0 OR patient_id = :patientId) "
            + "AND (:status = '' OR status = :status) "
            + "ORDER BY starts_at, id LIMIT :limite",
           nativeQuery = true)
    List<AppointmentRow> search(Long companyId, Instant desde, Instant hasta, long professionalId,
                                long resourceId, long patientId, String status, int limite);
}
