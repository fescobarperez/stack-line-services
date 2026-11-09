package com.erp_maya.ingestion.client;

import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuración del servicio Python aislado de crawling. */
@ConfigurationProperties("erp.crawling")
public class CrawlingClientProperties {
    private String serviceUrl = "http://localhost:8090";
    private String storageRoot = ".local/crawling";
    private int pollSeconds = 2;
    private int timeoutSeconds = 1800;

    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
    public String getStorageRoot() { return storageRoot; }
    public void setStorageRoot(String storageRoot) { this.storageRoot = storageRoot; }
    public int getPollSeconds() { return pollSeconds; }
    public void setPollSeconds(int pollSeconds) { this.pollSeconds = pollSeconds; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
