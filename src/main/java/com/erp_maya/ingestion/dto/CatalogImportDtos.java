package com.erp_maya.ingestion.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CatalogImportDtos {

    private CatalogImportDtos() {}

    @Serdeable
    public record PreviewRequest(@NotNull Long supplierId,
                                 Long supplierWebsiteId,
                                 @NotBlank String sourceType,
                                 String sourceReference,
                                 String userPrompt,
                                 @NotNull @Valid List<Row> items) {}

    @Serdeable
    public record Row(@NotBlank String name,
                      BigDecimal unitCost,
                      String currency,
                      String sourceUrl,
                      String productUrl,
                      List<String> categoryPath,
                      String supplierSku,
                      String brand,
                      String manufacturerName,
                      String manufacturerPartNumber,
                      String gtin,
                      String unit,
                      BigDecimal packSize,
                      String description,
                      Map<String, Object> attributes) {}

    @Serdeable
    public record SelectionRequest(List<Long> itemIds) {}

    @Serdeable
    public record DecisionRequest(@NotBlank String action,
                                  Long targetProductId) {}

    @Serdeable
    public record BatchResponse(Long id, String sourceType, String status, int itemCount,
                                List<ItemResponse> items) {}

    @Serdeable
    public record ItemResponse(Long id, Integer rowNumber, String sourceProductName, String action, Long targetProductId,
                               List<String> currentCategoryPath, List<String> proposedCategoryPath,
                               BigDecimal currentUnitCost, BigDecimal proposedUnitCost,
                               Map<String, Object> confidence, String decisionReason,
                               Map<String, Object> evidence,
                               List<Map<String, Object>> alternativeCandidates,
                               String status) {}
}
