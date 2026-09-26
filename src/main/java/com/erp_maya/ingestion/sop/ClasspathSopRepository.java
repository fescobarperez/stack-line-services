package com.erp_maya.ingestion.sop;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** SOPs embebidos en la aplicación mientras el almacenamiento S3 queda diferido. */
@Singleton
public class ClasspathSopRepository implements SopRepository {

    private static final Map<String, ResourcePair> RESOURCES = Map.of(
            "san-miguel", new ResourcePair(
                    "san-miguel-catalog",
                    "v1",
                    "ai/sops/san-miguel/catalog-v1.md",
                    "ai/sops/san-miguel/catalog-v1.json")
    );

    @Override
    public Optional<SopDocument> findActive(String siteKey) {
        ResourcePair pair = RESOURCES.get(siteKey);
        if (pair == null) return Optional.empty();
        return Optional.of(new SopDocument(siteKey, pair.id(), pair.version(),
                read(pair.markdownPath()), read(pair.rulesPath())));
    }

    private String read(String resourcePath) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("No se encontró el recurso SOP " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el recurso SOP " + resourcePath, ex);
        }
    }

    private record ResourcePair(String id, String version, String markdownPath, String rulesPath) {}
}
