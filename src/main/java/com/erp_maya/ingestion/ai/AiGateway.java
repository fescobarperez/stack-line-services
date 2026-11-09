package com.erp_maya.ingestion.ai;

import java.util.Map;

/** Gateway del modelo conversacional; el MVP puede usar fake o un adapter externo. */
public interface AiGateway {
    Map<String, Object> generateStructured(String instruction, Map<String, Object> context);
    String provider();
    String model();
}
