package com.erp_maya.partner.controller;

import com.erp_maya.partner.dto.ClientDtos;
import com.erp_maya.partner.service.ClientService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @Get
    public Page<ClientDtos.Response> list(@Nullable @QueryValue String search, Pageable pageable) {
        return service.list(search, pageable);
    }

    /** Autocompletado por NIT en cotizaciones: 200 con el cliente o 404. */
    @Get("/by-nit/{nit}")
    public ClientDtos.Response byNit(String nit) {
        return service.findByNit(nit).orElseThrow(
                () -> new com.erp_maya.common.ResourceNotFoundException("Sin cliente con NIT " + nit));
    }

    @Get("/{id}")
    public ClientDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public ClientDtos.Response create(@Valid @Body ClientDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public ClientDtos.Response update(Long id, @Valid @Body ClientDtos.Request request) {
        return service.update(id, request);
    }

    @Delete("/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        service.delete(id);
    }
}
