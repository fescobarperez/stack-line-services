package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Appointment;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Citas activas (no canceladas ni «no asistió») que se cruzan con
     * [desde, hasta) para ese profesional o ese recurso, sin contar `excluir`.
     * Los ids que no aplican llegan como -1. Es la misma regla que la
     * restricción EXCLUDE de la base: aquí sirve para dar un mensaje claro y
     * para calcular la disponibilidad; la base es la que la garantiza.
     */
    @Query(value = "SELECT * FROM salud_appointments WHERE company_id = :companyId AND id <> :excluir "
            + "AND status NOT IN ('cancelada', 'no_asistio') "
            + "AND starts_at < :hasta AND ends_at > :desde "
            + "AND (professional_id = :professionalId OR resource_id = :resourceId) ORDER BY starts_at",
           nativeQuery = true)
    List<Appointment> activeOverlapping(Long companyId, Instant desde, Instant hasta,
                                        long professionalId, long resourceId, long excluir);

    /** Citas activas de la empresa en el rango, de todos los recursos (para disponibilidad). */
    @Query(value = "SELECT * FROM salud_appointments WHERE company_id = :companyId "
            + "AND status NOT IN ('cancelada', 'no_asistio') "
            + "AND starts_at < :hasta AND ends_at > :desde ORDER BY starts_at",
           nativeQuery = true)
    List<Appointment> activeInRange(Long companyId, Instant desde, Instant hasta);

    /** Citas de un plan de tratamiento (fase 7), para su avance. */
    List<Appointment> findByPlanId(Long planId);
}
