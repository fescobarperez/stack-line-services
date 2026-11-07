package com.erp_maya.ingestion.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/** Cliente del servicio Python; no ejecuta crawler dentro de la JVM. */
@Singleton
public class CrawlingServiceClient {
    private final CrawlingClientProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public CrawlingServiceClient(CrawlingClientProperties properties) {
        this.properties = properties;
        this.mapper = new ObjectMapper();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Object> run(Map<String, Object> payload) {
        return run(payload, ignored -> { });
    }

    public Map<String, Object> run(Map<String, Object> payload, Consumer<Map<String, Object>> onProgress) {
        String runId = String.valueOf(payload.get("run_id"));
        post("/v1/crawl", payload);
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.getTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> state = get("/v1/crawl/" + runId);
            String status = String.valueOf(state.getOrDefault("status", "RUNNING"));
            if ("RUNNING".equalsIgnoreCase(status)) {
                onProgress.accept(state);
            }
            if (!"RUNNING".equalsIgnoreCase(status)) {
                Object result = state.get("result");
                if (result instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
                return state;
            }
            try {
                Thread.sleep(Math.max(250, properties.getPollSeconds() * 1000L));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("El polling del crawling fue interrumpido", error);
            }
        }
        throw new IllegalStateException("El servicio de crawling excedió el tiempo máximo de espera");
    }

    private Map<String, Object> post(String path, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getServiceUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("El servicio de crawling respondió HTTP " + response.statusCode() + ": " + response.body());
            }
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo contactar el servicio de crawling", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La llamada al servicio de crawling fue interrumpida", error);
        }
    }

    private Map<String, Object> get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getServiceUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("El servicio de crawling respondió HTTP " + response.statusCode() + ": " + response.body());
            }
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo consultar el servicio de crawling", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La consulta al servicio de crawling fue interrumpida", error);
        }
    }
}
