package com.erp_maya.ingestion.ai;

/** El modelo genera el vector; pgvector solo lo almacena y busca. */
public interface EmbeddingGateway {
    float[] embed(String text);
    String provider();
    String model();
}
