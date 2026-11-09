package com.erp_maya.ingestion.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fallback local para preview: recupera candidatos por nombre y deja la
 * estrategia preparada para sumar búsqueda textual/vectorial sin cambiar el
 * contrato de decisiones.
 */
@Singleton
public class CatalogCandidateService {

    private static final int LIMIT = 10;

    private final ProductRepository products;
    private final ProductSearchDocumentService semanticSearch;

    public CatalogCandidateService(ProductRepository products, ProductSearchDocumentService semanticSearch) {
        this.products = products;
        this.semanticSearch = semanticSearch;
    }

    public Decision decide(Long companyId, String incomingName) {
        String name = incomingName == null ? "" : incomingName.trim();
        if (name.isBlank()) {
            return new Decision("REVIEW", null, confidence(0.0, 0.0, 0.0),
                    "El producto no tiene nombre", evidence("deterministic", "missing_name"), List.of());
        }

        List<Product> candidates = semanticSearch.search(companyId, name, LIMIT).stream()
                .map(vector -> products.findByIdAndCompanyId(vector.productId(), companyId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (candidates.isEmpty()) {
            candidates = products
                    .findByCompanyIdAndNameContainsIgnoreCase(companyId, name, Pageable.from(0, LIMIT))
                    .getContent();
        }
        if (candidates.isEmpty()) {
            String normalizedIncoming = normalize(name);
            candidates = products.findByCompanyIdAndReviewStatusIsNull(companyId, Pageable.from(0, 100)).getContent().stream()
                    .filter(product -> tokenOverlap(normalizedIncoming, normalize(product.getName())) >= 0.45)
                    .limit(LIMIT)
                    .toList();
        }

        if (candidates.isEmpty()) {
            return new Decision("CREATE", null, confidence(0.0, 0.35, 0.35),
                    "No se encontró un candidato en el catálogo actual",
                    evidence("deterministic", "no_candidate"), List.of());
        }

        Product best = candidates.get(0);
        double score = similarity(name, best.getName());
        List<Map<String, Object>> alternatives = new ArrayList<>();
        for (Product candidate : candidates) {
            Map<String, Object> alternative = new LinkedHashMap<>();
            alternative.put("productId", candidate.getId());
            alternative.put("name", candidate.getName());
            alternative.put("sku", candidate.getSku());
            alternative.put("score", round(similarity(name, candidate.getName())));
            alternatives.add(alternative);
        }

        if (normalize(name).equals(normalize(best.getName()))) {
            return new Decision("LINK_EXISTING_PRODUCT", best.getId(), confidence(score, 0.95, 0.95),
                    "El nombre normalizado coincide exactamente con un producto existente",
                    evidence("deterministic", "normalized_name_exact"), alternatives);
        }

        return new Decision("REVIEW", best.getId(), confidence(score, 0.55, 0.55),
                "Se encontraron candidatos por nombre; requiere revisión de la evidencia",
                evidence("deterministic", "name_candidate"), alternatives);
    }

    private static Map<String, Object> confidence(double match, double action, double category) {
        return Map.of("match", round(match), "action", round(action), "category", round(category));
    }

    private static Map<String, Object> evidence(String source, String method) {
        return Map.of("source", source, "method", method);
    }

    private static double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.equals(b)) return 1.0;
        return tokenOverlap(a, b);
    }

    private static double tokenOverlap(String left, String right) {
        if (left.isBlank() || right.isBlank()) return 0.0;
        List<String> a = List.of(left.split("\\s+"));
        List<String> b = List.of(right.split("\\s+"));
        long shared = a.stream().filter(b::contains).distinct().count();
        return (double) shared / Math.max(a.stream().distinct().count(), b.stream().distinct().count());
    }

    private static String normalize(String value) {
        String noAccent = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccent.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public record Decision(String action, Long targetProductId,
                           Map<String, Object> confidence, String reason,
                           Map<String, Object> evidence,
                           List<Map<String, Object>> alternatives) {}
}
