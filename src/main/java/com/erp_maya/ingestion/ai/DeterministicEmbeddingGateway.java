package com.erp_maya.ingestion.ai;

import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Vector local para pruebas; no debe usarse como calidad semántica de producción. */
@Singleton
public class DeterministicEmbeddingGateway implements EmbeddingGateway {

    private static final int DIMENSIONS = 1536;
    private final AiModelProperties properties;

    public DeterministicEmbeddingGateway(AiModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        byte[] digest = digest(text == null ? "" : text);
        for (int i = 0; i < vector.length; i++) {
            int unsigned = digest[i % digest.length] & 0xff;
            vector[i] = (unsigned / 127.5f) - 1.0f;
        }
        return vector;
    }

    @Override public String provider() { return properties.getEmbeddingProvider(); }
    @Override public String model() { return properties.getEmbeddingModel(); }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
