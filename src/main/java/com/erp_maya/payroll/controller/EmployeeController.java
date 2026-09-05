package com.erp_maya.payroll.controller;

import com.erp_maya.payroll.dto.EmployeeDtos;
import com.erp_maya.payroll.service.EmployeeService;
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

@Controller("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @Get
    public List<EmployeeDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public EmployeeDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public EmployeeDtos.Response create(@Valid @Body EmployeeDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public EmployeeDtos.Response update(Long id, @Valid @Body EmployeeDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
