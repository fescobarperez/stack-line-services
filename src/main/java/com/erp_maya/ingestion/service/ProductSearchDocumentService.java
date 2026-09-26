package com.erp_maya.ingestion.service;

import com.erp_maya.ingestion.ai.EmbeddingGateway;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Proyección opcional de búsqueda semántica. Si la extensión aún no está
 * instalada, el caller puede seguir usando la búsqueda textual sin romper la
 * preview ni el catálogo operativo.
 */
@Singleton
public class ProductSearchDocumentService {

    private final EntityManager entityManager;
    private final EmbeddingGateway embeddings;

    public ProductSearchDocumentService(EntityManager entityManager, EmbeddingGateway embeddings) {
        this.entityManager = entityManager;
        this.embeddings = embeddings;
    }

    @Transactional(TxType.REQUIRES_NEW)
    public boolean index(Long companyId, Long productId, String searchText, Map<String, Object> attributes) {
        try {
            String vector = vectorLiteral(embeddings.embed(searchText));
            entityManager.createNativeQuery("""
                    INSERT INTO product_search_documents
                      (company_id, product_id, search_text, normalized_attributes,
                       embedding, embedding_provider, embedding_model, embedding_version,
                       content_hash, indexed_at, updated_at)
                    VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS vector), ?, ?, 'mvp-1', md5(?), now(), now())
                    ON CONFLICT (company_id, product_id) DO UPDATE SET
                      search_text = EXCLUDED.search_text,
                      normalized_attributes = EXCLUDED.normalized_attributes,
                      embedding = EXCLUDED.embedding,
                      embedding_provider = EXCLUDED.embedding_provider,
                      embedding_model = EXCLUDED.embedding_model,
                      embedding_version = EXCLUDED.embedding_version,
                      content_hash = EXCLUDED.content_hash,
                      indexed_at = now(),
                      updated_at = now()
                    """)
                    .setParameter(1, companyId)
                    .setParameter(2, productId)
                    .setParameter(3, searchText)
                    .setParameter(4, toJson(attributes))
                    .setParameter(5, vector)
                    .setParameter(6, embeddings.provider())
                    .setParameter(7, embeddings.model())
                    .setParameter(8, searchText)
                    .executeUpdate();
            return true;
        } catch (RuntimeException ignored) {
            // La búsqueda textual sigue siendo válida mientras pgvector no esté disponible.
            return false;
        }
    }

    @Transactional(TxType.REQUIRES_NEW)
    public List<VectorCandidate> search(Long companyId, String query, int limit) {
        try {
            String vector = vectorLiteral(embeddings.embed(query));
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery("""
                    SELECT product_id, 1 - (embedding <=> CAST(? AS vector)) AS score
                    FROM product_search_documents
                    WHERE company_id = ? AND embedding IS NOT NULL
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """)
                    .setParameter(1, vector)
                    .setParameter(2, companyId)
                    .setParameter(3, vector)
                    .setParameter(4, limit)
                    .getResultList();
            return rows.stream().map(row -> new VectorCandidate(
                    ((Number) row[0]).longValue(), ((Number) row[1]).doubleValue())).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static String vectorLiteral(float[] values) {
        return "[" + IntStream.range(0, values.length)
                .mapToObj(index -> Float.toString(values[index]))
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private static String toJson(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) return "{}";
        // Los atributos del MVP llegan normalizados y no contienen SQL; esta
        // serialización mínima evita acoplar el gateway a un ObjectMapper.
        String body = attributes.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\""
                        + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{" + body + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record VectorCandidate(Long productId, double score) {}
}
