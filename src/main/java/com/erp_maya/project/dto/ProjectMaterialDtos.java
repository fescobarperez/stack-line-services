package com.erp_maya.project.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class ProjectMaterialDtos {

    @Serdeable
    public record GroupRequest(@NotBlank String name, Long parentGroupId, Integer sortOrder) {}

    @Serdeable
    public record MoveGroupRequest(Long parentGroupId) {}

    @Serdeable
    public record RenameGroupRequest(@NotBlank String name) {}

    @Serdeable
    public record MaterialRequest(@NotNull Long productId, Long groupId,
                                  @NotNull BigDecimal quantityPlanned,
                                  String uom, @NotNull Long supplierId, String notes) {}

    @Serdeable
    public record MoveMaterialRequest(Long groupId) {}

    @Serdeable
    public record MaterialResponse(Long id, Long groupId, Long productId,
                                   String sku, String productName,
                                   BigDecimal quantityPlanned, String uom,
                                   Long supplierId, String supplierName,
                                   BigDecimal unitCostSnapshot, BigDecimal estimatedAmount,
                                   String status, String notes, Instant costSnapshotAt,
                                   Long quoteId) {}

    @Serdeable
    public record GroupResponse(Long id, Long parentGroupId, String name,
                                Integer sortOrder, List<MaterialResponse> materials,
                                BigDecimal estimatedCost) {}

    @Serdeable
    public record PlanResponse(Long projectId, List<GroupResponse> groups,
                               List<MaterialResponse> ungrouped,
                               BigDecimal estimatedCost, Integer materialCount) {}
}
