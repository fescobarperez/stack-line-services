package com.erp_maya.project.controller;

import com.erp_maya.project.dto.ProjectDtos;
import com.erp_maya.project.service.ProjectService;
import io.micronaut.http.HttpStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.*;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @Get
    public List<ProjectDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public ProjectDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public ProjectDtos.Response create(@Valid @Body ProjectDtos.Request request) {
        return service.create(request);
    }

    /** Convierte una cotización aprobada en proyecto. Una sola vez. */
    @Post("/from-quote/{quoteId}")
    @Status(HttpStatus.CREATED)
    public ProjectDtos.Response fromQuote(Long quoteId, @Nullable @Body ProjectDtos.FromQuoteRequest request) {
        return service.fromQuote(quoteId, request);
    }

    @Post("/{id}/costs")
    public ProjectDtos.Response addCost(Long id, @Valid @Body ProjectDtos.CostRequest request,
                                        @Nullable Authentication authentication) {
        Object uid = authentication == null ? null : authentication.getAttributes().get("userId");
        return service.addCost(id, request, uid instanceof Number n ? n.longValue() : null);
    }

    /** Consume materia prima de bodega y la carga al proyecto al costo promedio. */
    @Post("/{id}/consume")
    public ProjectDtos.Response consume(Long id, @Valid @Body ProjectDtos.ConsumeRequest request,
                                        @Nullable Authentication authentication) {
        Object uid = authentication == null ? null : authentication.getAttributes().get("userId");
        return service.consumeMaterial(id, request, uid instanceof Number n ? n.longValue() : null);
    }

    @Delete("/{id}/costs/{costId}")
    @Status(HttpStatus.NO_CONTENT)
    public void deleteCost(Long id, Long costId) {
        service.deleteCost(id, costId);
    }

    /** `note` justifica cerrar con costo sin facturar; en los demás casos sobra. */
    @Put("/{id}/status/{status}")
    public ProjectDtos.Response setStatus(Long id, String status, @Nullable @QueryValue String note) {
        return service.setStatus(id, status, note);
    }
}
