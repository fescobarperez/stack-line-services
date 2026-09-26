package com.erp_maya.ingestion.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.ingestion.domain.CrawlRun;
import com.erp_maya.ingestion.dto.CatalogImportDtos;
import com.erp_maya.ingestion.dto.CrawlDtos;
import com.erp_maya.ingestion.repository.CrawlRunRepository;
import com.erp_maya.ingestion.sop.SopDocument;
import com.erp_maya.ingestion.sop.SopRepository;
import com.erp_maya.partner.domain.SupplierWebsite;
import com.erp_maya.partner.repository.SupplierWebsiteRepository;
import jakarta.inject.Singleton;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class CrawlService {

    private static final Pattern PRICE = Pattern.compile("(?i)(?:GTQ|Q)\\s*([0-9]{1,3}(?:[,.][0-9]{3})*(?:[.,][0-9]{1,2})?)");
    private static final Pattern PAGE = Pattern.compile("/page/(\\d+)(?:/|$)");
    private static final int DEFAULT_MAX_PAGES = 500;

    private final CrawlRunRepository runs;
    private final SupplierWebsiteRepository websites;
    private final SopRepository sops;
    private final CatalogImportService imports;
    private final TenantContext tenant;

    public CrawlService(CrawlRunRepository runs, SupplierWebsiteRepository websites,
                        SopRepository sops, CatalogImportService imports, TenantContext tenant) {
        this.runs = runs;
        this.websites = websites;
        this.sops = sops;
        this.imports = imports;
        this.tenant = tenant;
    }

    public CrawlDtos.Response start(Long websiteId, CrawlDtos.StartRequest request) {
        Long companyId = tenant.getCompanyId();
        SupplierWebsite website = websites.findByIdAndCompanyId(websiteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sitio del proveedor " + websiteId + " no encontrado"));
        if (!Boolean.TRUE.equals(website.getCrawlEnabled()) || !"active".equalsIgnoreCase(website.getStatus())) {
            throw new IllegalStateException("El crawling está desactivado para este sitio");
        }

        String siteKey = siteKey(website);
        SopDocument sop = sops.findActive(siteKey)
                .orElseThrow(() -> new IllegalStateException("No hay SOP activo para el sitio " + siteKey));
        CrawlRun run = new CrawlRun();
        run.setCompanyId(companyId);
        run.setSupplierWebsiteId(websiteId);
        run.setUserPrompt(request == null ? null : request.userPrompt());
        run.setSopVersion(sop.version());
        run.setStatus("running");
        runs.save(run);

        try {
            CrawlResult result = crawl(website, request, siteKey);
            CatalogImportDtos.PreviewRequest preview = new CatalogImportDtos.PreviewRequest(
                    website.getSupplierId(), websiteId, "crawl", website.getBaseUrl(),
                    request == null ? null : request.userPrompt(), result.rows(), run.getId());
            CatalogImportDtos.BatchResponse batch = imports.preview(preview);
            finish(run, result, "completed");
            return response(run, batch.id(), result);
        } catch (RuntimeException error) {
            run.setStatus("failed");
            run.setQualityStatus("failed");
            run.setQualityReport(Map.of("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            run.setCompletedAt(Instant.now());
            runs.update(run);
            throw error;
        }
    }

    private CrawlResult crawl(SupplierWebsite website, CrawlDtos.StartRequest request, String siteKey) {
        List<String> seeds = request == null || request.categoryUrls() == null || request.categoryUrls().isEmpty()
                ? List.of(website.getBaseUrl()) : request.categoryUrls().stream().filter(url -> url != null && !url.isBlank()).map(String::trim).toList();
        int maxPages = request == null || request.maxPages() == null ? DEFAULT_MAX_PAGES
                : Math.max(1, Math.min(DEFAULT_MAX_PAGES, request.maxPages()));
        Deque<String> queue = new ArrayDeque<>();
        Set<String> discovered = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> categoryRoots = new LinkedHashSet<>();
        List<String> failures = new ArrayList<>();
        Map<String, CatalogImportDtos.Row> products = new LinkedHashMap<>();
        for (String seed : seeds) {
            String canonical = canonicalUrl(seed, website.getBaseUrl());
            queue.add(canonical);
            discovered.add(canonical);
            categoryRoots.add(stripPage(canonical));
        }

        while (!queue.isEmpty() && visited.size() < maxPages) {
            String pageUrl = queue.removeFirst();
            if (!visited.add(pageUrl)) continue;
            try {
                Document document = Jsoup.connect(pageUrl).userAgent("StacklineCatalogCrawler/1.0")
                        .timeout(20_000).followRedirects(true).get();
                String category = firstHeading(document);
                extractProducts(document, pageUrl, category, products, website.getBaseUrl());
                for (Element link : document.select("a[href]")) {
                    String candidate = canonicalUrl(link.absUrl("href"), website.getBaseUrl());
                    if (!isPagination(candidate) || !categoryRoots.contains(stripPage(candidate))) continue;
                    if (discovered.add(candidate)) queue.addLast(candidate);
                }
            } catch (Exception error) {
                failures.add(pageUrl + " :: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            }
        }

        boolean hitLimit = !queue.isEmpty();
        String quality = failures.isEmpty() && !hitLimit ? "complete" : "incomplete";
        if (visited.isEmpty() || products.isEmpty()) quality = failures.isEmpty() ? "partial" : "failed";
        return new CrawlResult(new ArrayList<>(products.values()), discovered, visited, failures, hitLimit, quality);
    }

    private void extractProducts(Document document, String listingUrl, String category,
                                 Map<String, CatalogImportDtos.Row> products, String baseUrl) {
        for (Element link : document.select("a[href]")) {
            String productUrl = canonicalUrl(link.absUrl("href"), baseUrl);
            if (!isProductUrl(productUrl)) continue;
            String name = clean(link.text());
            if (name.isBlank()) {
                Element heading = link.selectFirst("h6, h5, h4");
                name = heading == null ? "" : clean(heading.text());
            }
            if (name.isBlank()) continue;
            Element card = link.closest("article, li, .oe_product, .oe_product_cart, .card");
            String priceText = card == null ? clean(link.parent() == null ? "" : link.parent().text()) : clean(card.text());
            BigDecimal price = price(priceText);
            CatalogImportDtos.Row row = new CatalogImportDtos.Row(
                    name, price, "GTQ", listingUrl, productUrl,
                    category.isBlank() ? List.of() : List.of(category),
                    skuFromUrl(productUrl), null, null, null, null, null, null,
                    null, Map.of("crawler", "san-miguel-v1"));
            CatalogImportDtos.Row previous = products.get(productUrl);
            if (previous == null || (previous.unitCost() == null && price != null)) products.put(productUrl, row);
        }
    }

    private void finish(CrawlRun run, CrawlResult result, String status) {
        run.setStatus(status);
        run.setQualityStatus(result.qualityStatus());
        run.setPagesDiscovered(result.discovered().size());
        run.setPagesVisited(result.visited().size());
        run.setPagesFailed(result.failures().size());
        run.setProductsExtracted(result.rows().size());
        run.setQualityReport(Map.of(
                "failedUrls", result.failures(),
                "hitMaxPages", result.hitLimit(),
                "visitedUrls", result.visited(),
                "discoveredUrls", result.discovered()));
        run.setCompletedAt(Instant.now());
        runs.update(run);
    }

    private CrawlDtos.Response response(CrawlRun run, Long batchId, CrawlResult result) {
        return new CrawlDtos.Response(run.getId(), batchId, run.getStatus(), run.getQualityStatus(),
                run.getPagesDiscovered(), run.getPagesVisited(), run.getPagesFailed(),
                run.getProductsExtracted(), result.failures(), run.getQualityReport(), run.getCompletedAt());
    }

    private static String siteKey(SupplierWebsite website) {
        String value = (website.getName() + " " + website.getBaseUrl()).toLowerCase();
        if (value.contains("san miguel") || value.contains("sanmiguelmadera")) return "san-miguel";
        throw new IllegalStateException("No hay SOP configurado para este sitio");
    }

    private static boolean isProductUrl(String url) {
        try {
            String path = new URI(url).getPath();
            return path != null
                    && path.startsWith("/shop/")
                    && !path.startsWith("/shop/category/")
                    && path.matches("/shop/.+-\\d+");
        } catch (URISyntaxException error) { return false; }
    }

    private static boolean isPagination(String url) { return PAGE.matcher(url).find(); }

    private static String stripPage(String url) { return url.replaceFirst("/page/\\d+(?=/|$)", ""); }

    private static String canonicalUrl(String raw, String baseUrl) {
        try {
            URI uri = new URI(raw);
            if (!uri.isAbsolute()) uri = new URI(baseUrl).resolve(uri);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null).toString();
        } catch (Exception error) { return raw; }
    }

    private static String firstHeading(Document document) {
        Element h1 = document.selectFirst("h1");
        return h1 == null ? "" : clean(h1.text());
    }

    private static String clean(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim(); }

    private static BigDecimal price(String text) {
        Matcher matcher = PRICE.matcher(text == null ? "" : text);
        if (!matcher.find()) return null;
        String raw = matcher.group(1).trim();
        boolean hasComma = raw.contains(",");
        boolean hasDot = raw.contains(".");
        if (hasComma && hasDot) {
            if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) {
                raw = raw.replace(".", "").replace(',', '.');
            } else {
                raw = raw.replace(",", "");
            }
        } else if (hasComma) {
            raw = raw.matches(".*,[0-9]{1,2}$") ? raw.replace(',', '.') : raw.replace(",", "");
        } else if (hasDot && !raw.matches(".*\\.[0-9]{1,2}$")) {
            raw = raw.replace(".", "");
        }
        try { return new BigDecimal(raw); } catch (NumberFormatException error) { return null; }
    }

    private static String skuFromUrl(String url) {
        Matcher matcher = Pattern.compile("-(\\d+)(?:$|\\?)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record CrawlResult(List<CatalogImportDtos.Row> rows, Set<String> discovered,
                               Set<String> visited, List<String> failures,
                               boolean hitLimit, String qualityStatus) {}
}
