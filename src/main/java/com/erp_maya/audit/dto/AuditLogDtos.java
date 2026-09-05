package com.erp_maya.audit.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

public final class AuditLogDtos {

    private AuditLogDtos() {}

    @Serdeable
    public record Request(Long userId, String module, String action, String severity,
                          String description, String entityRef, Long branchId, String ipAddress) {}

    @Serdeable
    public record Response(Long id, Long userId, String userName, Instant occurredAt, String module,
                           String action, String severity, String description, String entityRef,
                           Long branchId, String ipAddress) {}
}
