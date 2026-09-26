package com.erp_maya.ingestion.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CrawlDtos {
    private CrawlDtos() {}

    @Serdeable
    public record StartRequest(List<String> categoryUrls, String userPrompt, Integer maxPages) {}

    @Serdeable
    public record Response(Long crawlRunId, Long importBatchId, String status,
                           String qualityStatus, Integer pagesDiscovered,
                           Integer pagesVisited, Integer pagesFailed,
                           Integer productsExtracted, List<String> failedUrls,
                           Map<String, Object> qualityReport, Instant completedAt) {}
}
