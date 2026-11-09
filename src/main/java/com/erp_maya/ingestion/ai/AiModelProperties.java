package com.erp_maya.ingestion.ai;

import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuración global de experimentación del MVP; no es configuración por empresa. */
@ConfigurationProperties("erp.ai")
public class AiModelProperties {

    private String mode = "fake";
    private String chatProvider = "openai";
    private String chatModel = "gpt-mini";
    private String embeddingProvider = "openai";
    private String embeddingModel = "text-embedding-3-small";

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getChatProvider() { return chatProvider; }
    public void setChatProvider(String chatProvider) { this.chatProvider = chatProvider; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String embeddingProvider) { this.embeddingProvider = embeddingProvider; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
}
