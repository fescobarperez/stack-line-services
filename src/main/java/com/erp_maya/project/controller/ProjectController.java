package com.erp_maya.project.controller;

import com.erp_maya.project.dto.ProjectDtos;
import com.erp_maya.project.dto.ProjectMaterialDtos;
import com.erp_maya.project.dto.ProjectQuoteBuilderDtos;
import com.erp_maya.project.service.ProjectMaterialService;
import com.erp_maya.project.service.ProjectQuoteBuilderService;
import com.erp_maya.project.service.ProjectService;
import io.micronaut.http.HttpStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.*;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@Controller("/api/projects")
public class ProjectController {

    private final ProjectService service;
    private final ProjectMaterialService materialService;
    private final ProjectQuoteBuilderService quoteBuilder;

    public ProjectController(ProjectService service, ProjectMaterialService materialService,
                             ProjectQuoteBuilderService quoteBuilder) {
        this.service = service;
        this.materialService = materialService;
        this.quoteBuilder = quoteBuilder;
    }

    /**
     * Crea una cotización a partir de materiales del proyecto. Cada línea agrupa
     * materiales del pool disponible; el cliente ve solo descripción + precio.
     * Devuelve el id de la cotización creada.
     */
    @Post("/{id}/quotes")
    @Status(HttpStatus.CREATED)
    public Map<String, Long> createQuoteFromMaterials(Long id,
                                                       @Valid @Body ProjectQuoteBuilderDtos.Request request) {
        return Map.of("quoteId", quoteBuilder.createFromMaterials(id, request));
    }

    @Get
    public List<ProjectDtos.Response> list() {
        return service.list();
    }

    @Get("/{id}")
    public ProjectDtos.Response get(Long id) {
        return service.get(id);
    }

    @Get("/{id}/materials")
    public ProjectMaterialDtos.PlanResponse materials(Long id) {
        return materialService.getPlan(id);
    }

    @Post("/{id}/material-groups")
    @Status(HttpStatus.CREATED)
    public ProjectMaterialDtos.GroupResponse createMaterialGroup(Long id,
                                                                  @Valid @Body ProjectMaterialDtos.GroupRequest request) {
        return materialService.createGroup(id, request);
    }

    @Post("/{id}/materials")
    @Status(HttpStatus.CREATED)
    public ProjectMaterialDtos.MaterialResponse createMaterial(Long id,
                                                                @Valid @Body ProjectMaterialDtos.MaterialRequest request) {
        return materialService.createMaterial(id, request);
    }

    @Put("/{id}/materials/{materialId}/group")
    public ProjectMaterialDtos.MaterialResponse moveMaterial(Long id, Long materialId,
                                                              @Body ProjectMaterialDtos.MoveMaterialRequest request) {
        return materialService.moveMaterial(id, materialId, request);
    }

    @Put("/{id}/material-groups/{groupId}/parent")
    public ProjectMaterialDtos.GroupResponse moveMaterialGroup(Long id, Long groupId,
                                                                 @Body ProjectMaterialDtos.MoveGroupRequest request) {
        return materialService.moveGroup(id, groupId, request);
    }

    @Post("/{id}/materials/{materialId}/copy")
    public ProjectMaterialDtos.MaterialResponse copyMaterial(Long id, Long materialId) {
        return materialService.copyMaterial(id, materialId);
    }

    @Post("/{id}/material-groups/{groupId}/copy")
    public ProjectMaterialDtos.GroupResponse copyMaterialGroup(Long id, Long groupId) {
        return materialService.copyGroup(id, groupId);
    }

    @Put("/{id}/material-groups/{groupId}")
    public ProjectMaterialDtos.GroupResponse renameMaterialGroup(Long id, Long groupId,
                                                                   @Valid @Body ProjectMaterialDtos.RenameGroupRequest request) {
        return materialService.renameGroup(id, groupId, request);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public ProjectDtos.Response create(@Valid @Body ProjectDtos.Request request) {
        return service.create(request);
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
