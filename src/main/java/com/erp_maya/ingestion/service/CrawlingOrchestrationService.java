package com.erp_maya.ingestion.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.ingestion.client.CrawlingClientProperties;
import com.erp_maya.ingestion.client.CrawlingServiceClient;
import com.erp_maya.ingestion.domain.CrawlRun;
import com.erp_maya.ingestion.dto.CatalogImportDtos;
import com.erp_maya.ingestion.dto.CrawlDtos;
import com.erp_maya.ingestion.repository.CatalogImportBatchRepository;
import com.erp_maya.ingestion.repository.CrawlRunRepository;
import com.erp_maya.ingestion.sop.SopDocument;
import com.erp_maya.ingestion.sop.SopRepository;
import com.erp_maya.partner.domain.SupplierWebsite;
import com.erp_maya.partner.repository.SupplierWebsiteRepository;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Orquesta corridas; la extracción vive en el repositorio Python separado. */
@Singleton
public class CrawlingOrchestrationService {
    private final CrawlRunRepository runs;
    private final CatalogImportBatchRepository batches;
    private final SupplierWebsiteRepository websites;
    private final SopRepository sops;
    private final CatalogImportService imports;
    private final CrawlingServiceClient crawler;
    private final CrawlingClientProperties properties;
    private final TenantContext tenant;
    private final Map<String, Long> active = new ConcurrentHashMap<>();
    private final Map<String, Long> idempotent = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "stackline-crawling-orchestrator");
        thread.setDaemon(true);
        return thread;
    });

    public CrawlingOrchestrationService(CrawlRunRepository runs,
                                        CatalogImportBatchRepository batches,
                                        SupplierWebsiteRepository websites,
                                        SopRepository sops,
                                        CatalogImportService imports,
                                        CrawlingServiceClient crawler,
                                        CrawlingClientProperties properties,
                                        TenantContext tenant) {
        this.runs = runs;
        this.batches = batches;
        this.websites = websites;
        this.sops = sops;
        this.imports = imports;
        this.crawler = crawler;
        this.properties = properties;
        this.tenant = tenant;
    }

    public CrawlDtos.Response start(Long websiteId, CrawlDtos.StartRequest request) {
        Long companyId = tenant.getCompanyId();
        SupplierWebsite website = websites.findByIdAndCompanyId(websiteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sitio del proveedor " + websiteId + " no encontrado"));
        if (!Boolean.TRUE.equals(website.getCrawlEnabled()) || !"active".equalsIgnoreCase(website.getStatus())) {
            throw new IllegalStateException("El crawling está desactivado para este sitio");
        }
        CrawlDtos.StartRequest safeRequest = request == null
                ? new CrawlDtos.StartRequest(null, null, null, null)
                : request;
        String key = companyId + ":" + websiteId;
        String idempotencyKey = blankToNull(safeRequest.idempotencyKey());
        if (idempotencyKey != null) {
            Long previous = idempotent.get(key + ":" + idempotencyKey);
            if (previous != null) return get(previous);
        }
        Long alreadyActive = active.get(key);
        if (alreadyActive != null) {
            return get(alreadyActive);
        }
        var queued = runs.findFirstByCompanyIdAndSupplierWebsiteIdAndStatusOrderByCreatedAtDesc(companyId, websiteId, "queued");
        if (queued.isPresent()) return response(queued.get());
        var running = runs.findFirstByCompanyIdAndSupplierWebsiteIdAndStatusOrderByCreatedAtDesc(companyId, websiteId, "running");
        if (running.isPresent()) return response(running.get());
        String siteUrl = crawlUrl(safeRequest, website);
        if (batches.existsByCompanyIdAndSupplierWebsiteIdAndSourceTypeAndSourceReferenceAndStatus(
                companyId, websiteId, "crawl", siteUrl, "preview")) {
            throw new IllegalStateException("La página ya tiene una propuesta de crawling pendiente; apruébala o recházala antes de iniciar otra corrida para esa página");
        }

        String siteKey = siteKey(website);
        SopDocument sop = sops.findActive(siteKey)
                .orElseThrow(() -> new IllegalStateException("No hay SOP activo para el sitio " + siteKey));
        CrawlRun run = new CrawlRun();
        run.setCompanyId(companyId);
        run.setSupplierWebsiteId(websiteId);
        run.setUserPrompt(safeRequest.userPrompt());
        run.setSopVersion(sop.version());
        run.setStatus("queued");
        CrawlRun saved = runs.save(run);
        active.put(key, saved.getId());
        if (idempotencyKey != null) idempotent.put(key + ":" + idempotencyKey, saved.getId());
        executor.submit(() -> execute(saved.getId(), companyId, website, safeRequest, sop, key));
        return response(saved);
    }

    public CrawlDtos.Response get(Long runId) {
        return response(runs.findByIdAndCompanyId(runId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Corrida de crawling " + runId + " no encontrada")));
    }

    private void execute(Long runId, Long companyId, SupplierWebsite website,
                         CrawlDtos.StartRequest request, SopDocument sop, String activeKey) {
        CrawlRun run = runs.findByIdAndCompanyId(runId, companyId).orElse(null);
        if (run == null) return;
        try {
            run.setStatus("running");
            runs.update(run);
            String siteUrl = crawlUrl(request, website);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("run_id", "stackline-" + runId);
            payload.put("site_key", siteKey(website));
            payload.put("site_url", siteUrl);
            payload.put("sop", sop.instructionsMarkdown() + "\n\n" + sop.rulesJson());
            payload.put("user_prompt", request.userPrompt());
            payload.put("max_pages", request.maxPages());
            payload.put("model", "gpt-6-luna");
            payload.put("build_root", buildRoot(companyId, website).toString());

            Map<String, Object> result = crawler.run(payload, progress -> updateProgress(run, progress));
            Map<String, Object> metrics = map(result.get("metrics"));
            Map<String, Object> quality = map(result.get("quality"));
            run.setPagesDiscovered(integer(metrics.get("pages_discovered")));
            run.setPagesVisited(integer(metrics.get("pages_visited")));
            run.setPagesFailed(integer(metrics.get("pages_failed")));
            run.setProductsExtracted(integer(metrics.get("products_extracted")));
            run.setQualityStatus(qualityStatus(String.valueOf(quality.getOrDefault("state", "failed"))));
            run.setQualityReport(result);
            run.setCompletedAt(Instant.now());

            String finalStatus = String.valueOf(result.getOrDefault("status", "EXECUTION_FAILED"));
            if ("COMPLETED".equals(finalStatus)) {
                List<CatalogImportDtos.Row> rows = rows(result);
                CatalogImportDtos.PreviewRequest preview = new CatalogImportDtos.PreviewRequest(
                        website.getSupplierId(), website.getId(), "crawl", siteUrl,
                        request.userPrompt(), rows, runId);
                CatalogImportDtos.BatchResponse batch = imports.previewForCompany(companyId, preview);
                Map<String, Object> report = new LinkedHashMap<>(result);
                report.put("importBatchId", batch.id());
                run.setQualityReport(report);
                run.setStatus("completed");
            } else if ("QUALITY_FAILED".equals(finalStatus)) {
                // The DB status enum is queued/running/completed/failed/cancelled.
                // Keep quality failure detail in qualityStatus/qualityReport.
                run.setStatus("failed");
            } else {
                run.setStatus("failed");
            }
            runs.update(run);
        } catch (RuntimeException error) {
            // Persist only a value allowed by crawl_runs.status; otherwise the
            // recovery update itself fails and leaves the row stuck in running.
            run.setStatus("failed");
            run.setQualityStatus("failed");
            run.setQualityReport(Map.of("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            run.setCompletedAt(Instant.now());
            runs.update(run);
        } finally {
            active.remove(activeKey, runId);
        }
    }

    private void updateProgress(CrawlRun run, Map<String, Object> state) {
        Map<String, Object> progress = map(state.get("progress"));
        if (progress.isEmpty()) progress = state;
        run.setPagesDiscovered(integer(progress.get("pages_discovered")));
        run.setPagesVisited(integer(progress.get("pages_visited")));
        run.setPagesFailed(integer(progress.get("pages_failed")));
        run.setProductsExtracted(integer(progress.get("products_extracted")));
        Map<String, Object> report = new LinkedHashMap<>(run.getQualityReport() == null ? Map.of() : run.getQualityReport());
        report.put("progress", progress);
        run.setQualityReport(report);
        runs.update(run);
    }

    private Path buildRoot(Long companyId, SupplierWebsite website) {
        return Path.of(properties.getStorageRoot()).toAbsolutePath().normalize()
                .resolve("clients").resolve(String.valueOf(companyId))
                .resolve("suppliers").resolve(String.valueOf(website.getSupplierId()))
                .resolve("sites").resolve(String.valueOf(website.getId()))
                .resolve("build");
    }

    private CrawlDtos.Response response(CrawlRun run) {
        Map<String, Object> report = run.getQualityReport() == null ? Map.of() : run.getQualityReport();
        Long batchId = number(report.get("importBatchId"));
        List<String> failed = new ArrayList<>();
        Map<String, Object> metrics = map(report.get("metrics"));
        Object failedUrls = metrics.get("failed_urls");
        if (failedUrls instanceof List<?> values) values.forEach(value -> failed.add(String.valueOf(value)));
        Map<String, Object> progress = map(report.get("progress"));
        return new CrawlDtos.Response(run.getId(), batchId, run.getStatus(), run.getQualityStatus(),
                run.getPagesDiscovered(), run.getPagesVisited(), run.getPagesFailed(),
                run.getProductsExtracted(), failed,
                text(progress.get("stage"), null), realNumber(progress.get("elapsed_seconds")),
                realNumber(progress.get("last_heartbeat_at")), report, run.getCompletedAt());
    }

    private static List<CatalogImportDtos.Row> rows(Map<String, Object> result) {
        Map<String, Object> data = map(result.get("data"));
        Object raw = data.get("products");
        if (!(raw instanceof List<?> products)) throw new IllegalStateException("El resultado no contiene data.products");
        List<CatalogImportDtos.Row> rows = new ArrayList<>();
        for (Object value : products) {
            Map<String, Object> item = map(value);
            String name = String.valueOf(item.getOrDefault("name", "")).trim();
            if (name.isBlank()) throw new IllegalStateException("El resultado contiene un producto sin nombre");
            rows.add(new CatalogImportDtos.Row(name,
                    decimal(first(item, "unitCost", "price")),
                    text(first(item, "currency"), "GTQ"),
                    text(first(item, "sourceUrl", "source_url", "source_listing_url"), null),
                    text(first(item, "productUrl", "product_url"), null),
                    categoryPath(item),
                    text(first(item, "supplierSku", "supplier_sku"), null),
                    text(first(item, "brand"), null), text(first(item, "manufacturerName", "manufacturer_name"), null),
                    text(first(item, "manufacturerPartNumber", "manufacturer_part_number"), null),
                    text(first(item, "gtin"), null), text(first(item, "unit"), null),
                    decimal(first(item, "packSize", "pack_size")), text(first(item, "description"), null),
                    map(item.get("attributes"))));
        }
        return rows;
    }

    private static String crawlUrl(CrawlDtos.StartRequest request, SupplierWebsite website) {
        String raw = request.categoryUrls() != null && !request.categoryUrls().isEmpty()
                ? request.categoryUrls().get(0) : website.getBaseUrl();
        String value = raw == null || raw.isBlank() ? website.getBaseUrl() : raw.trim();
        return value;
    }

    private static String siteKey(SupplierWebsite website) {
        String value = (website.getName() + " " + website.getBaseUrl()).toLowerCase();
        if (value.contains("san miguel") || value.contains("sanmiguelmadera")) return "san-miguel";
        throw new IllegalStateException("No hay SOP configurado para este sitio");
    }

    private static String qualityStatus(String state) {
        return switch (state) {
            case "healthy", "first_run" -> "complete";
            case "degraded" -> "incomplete";
            default -> "failed";
        };
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Object first(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && (!(value instanceof String text) || !text.isBlank())) return value;
        }
        return null;
    }

    private static List<String> categoryPath(Map<String, Object> item) {
        Object value = first(item, "categoryPath", "category_path", "category");
        if (value instanceof List<?> values) return values.stream().map(String::valueOf).toList();
        if (value == null || String.valueOf(value).isBlank()) return List.of();
        return List.of(String.valueOf(value));
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Double realNumber(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try { return value == null ? null : Double.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
