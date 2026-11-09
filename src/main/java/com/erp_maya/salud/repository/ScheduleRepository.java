package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Schedule;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** El horario de un profesional se reemplaza completo al guardar. */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByCompanyId(Long companyId);

    List<Schedule> findByProfessionalIdAndCompanyId(Long professionalId, Long companyId);

    List<Schedule> findByProfessionalIdAndWeekday(Long professionalId, int weekday);

    void deleteByProfessionalId(Long professionalId);
}
