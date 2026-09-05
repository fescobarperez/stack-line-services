package com.erp_maya.audit.controller;

import com.erp_maya.audit.dto.AuditLogDtos;
import com.erp_maya.audit.service.AuditLogService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/audit-log")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @Get
    public Page<AuditLogDtos.Response> list(@Nullable @QueryValue String module, Pageable pageable) {
        return service.list(module, pageable);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public AuditLogDtos.Response create(@Valid @Body AuditLogDtos.Request request) {
        return service.create(request);
    }
}
