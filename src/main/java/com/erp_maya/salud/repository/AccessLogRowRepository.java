package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.AccessLogRow;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.GenericRepository;

@Repository
public interface AccessLogRowRepository extends GenericRepository<AccessLogRow, Long> {

    Page<AccessLogRow> findByPatientIdAndCompanyId(Long patientId, Long companyId, Pageable pageable);
}
