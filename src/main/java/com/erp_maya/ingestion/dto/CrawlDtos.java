package com.erp_maya.ingestion.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CrawlDtos {
    private CrawlDtos() {}

    @Serdeable
    public record StartRequest(List<String> categoryUrls, String userPrompt, Integer maxPages,
                               String idempotencyKey, List<ScopeItem> categoryScope) {}

    /** Categoría externa elegida: URL de origen y ruta de nombres desde la raíz. */
    @Serdeable
    public record ScopeItem(String id, String url, List<String> path) {}

    @Serdeable
    public record CategoryNode(String id, String name, String url, String parentId,
                               Integer depth, Boolean hasChildren) {}

    @Serdeable
    public record DiscoveryResponse(String runId, String status, List<CategoryNode> categories,
                                    Map<String, Object> metrics, String error) {}

    @Serdeable
    public record Response(Long crawlRunId, Long importBatchId, String status,
                           String qualityStatus, Integer pagesDiscovered,
                           Integer pagesVisited, Integer pagesFailed,
                           Integer productsExtracted, List<String> failedUrls,
                           String stage, Double elapsedSeconds, Double lastHeartbeatAt,
                           Map<String, Object> qualityReport, Instant completedAt) {}
}
