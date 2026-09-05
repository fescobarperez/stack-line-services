package com.erp_maya.security.controller;

import com.erp_maya.security.dto.RoleDtos;
import com.erp_maya.security.service.RoleService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @Get
    public List<RoleDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public RoleDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public RoleDtos.Response create(@Valid @Body RoleDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public RoleDtos.Response update(Long id, @Valid @Body RoleDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
