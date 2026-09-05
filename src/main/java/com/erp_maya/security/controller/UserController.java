package com.erp_maya.security.controller;

import com.erp_maya.security.dto.UserDtos;
import com.erp_maya.security.service.UserService;
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

@Controller("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @Get
    public List<UserDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public UserDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public UserDtos.Response create(@Valid @Body UserDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public UserDtos.Response update(Long id, @Valid @Body UserDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
