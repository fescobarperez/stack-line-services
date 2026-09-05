package com.erp_maya.audit.repository;

import com.erp_maya.audit.domain.AuditLog;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByCompanyIdOrderByOccurredAtDesc(Long companyId, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndModuleOrderByOccurredAtDesc(Long companyId, String module, Pageable pageable);
}
