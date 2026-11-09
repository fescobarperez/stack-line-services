package com.erp_maya.ingestion.ai;

import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/** Gateway local de decisiones: no representa calidad de producción. */
@Singleton
public class DeterministicAiGateway implements AiGateway {

    private final AiModelProperties properties;

    public DeterministicAiGateway(AiModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, Object> generateStructured(String instruction, Map<String, Object> context) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action", "REVIEW");
        response.put("confidence", Map.of("match", 0.0, "action", 0.0, "category", 0.0));
        response.put("reason", "Gateway determinista: requiere conectar un modelo externo para decidir");
        response.put("evidence", Map.of("provider", provider(), "model", model(), "mode", properties.getMode()));
        return response;
    }

    @Override public String provider() { return properties.getChatProvider(); }
    @Override public String model() { return properties.getChatModel(); }
}
