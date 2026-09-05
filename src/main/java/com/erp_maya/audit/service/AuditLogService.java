package com.erp_maya.audit.service;

import com.erp_maya.audit.domain.AuditLog;
import com.erp_maya.audit.dto.AuditLogDtos;
import com.erp_maya.audit.repository.AuditLogRepository;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.security.repository.UserRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class AuditLogService {

    private final AuditLogRepository logs;
    private final UserRepository users;
    private final BranchRepository branches;
    private final TenantContext tenant;

    public AuditLogService(AuditLogRepository logs, UserRepository users, BranchRepository branches, TenantContext tenant) {
        this.logs = logs;
        this.users = users;
        this.branches = branches;
        this.tenant = tenant;
    }

    @Transactional
    public Page<AuditLogDtos.Response> list(String module, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<AuditLog> page = (module != null && !module.isBlank())
                ? logs.findByCompanyIdAndModuleOrderByOccurredAtDesc(companyId, module, pageable)
                : logs.findByCompanyIdOrderByOccurredAtDesc(companyId, pageable);
        return page.map(AuditLogService::toResponse);
    }

    @Transactional
    public AuditLogDtos.Response create(AuditLogDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        AuditLog log = new AuditLog();
        log.setCompanyId(companyId);
        log.setModule(req.module());
        log.setAction(req.action());
        log.setSeverity(req.severity());
        log.setDescription(req.description());
        log.setEntityRef(req.entityRef());
        log.setIpAddress(req.ipAddress());
        if (req.userId() != null) {
            users.findByIdAndCompanyId(req.userId(), companyId).ifPresent(log::setUser);
        }
        if (req.branchId() != null) {
            branches.findByIdAndCompanyId(req.branchId(), companyId).ifPresent(log::setBranch);
        }
        return toResponse(logs.save(log));
    }

    private static AuditLogDtos.Response toResponse(AuditLog l) {
        return new AuditLogDtos.Response(l.getId(),
                l.getUser() != null ? l.getUser().getId() : null,
                l.getUser() != null ? l.getUser().getName() : null,
                l.getOccurredAt(), l.getModule(), l.getAction(), l.getSeverity(), l.getDescription(),
                l.getEntityRef(), l.getBranch() != null ? l.getBranch().getId() : null, l.getIpAddress());
    }
}
