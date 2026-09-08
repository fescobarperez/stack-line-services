package com.erp_maya.upload;

import io.micronaut.context.annotation.ConfigurationProperties;

/** Configuración de subida a S3 (bucket de logos). Poblada desde erp.s3.* */
@ConfigurationProperties("erp.s3")
public class S3UploadProperties {

    private String bucket;
    private String region = "us-east-1";
    private String logoPrefix = "logos/";
    private long presignExpirySeconds = 300;

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getLogoPrefix() { return logoPrefix; }
    public void setLogoPrefix(String logoPrefix) { this.logoPrefix = logoPrefix; }

    public long getPresignExpirySeconds() { return presignExpirySeconds; }
    public void setPresignExpirySeconds(long presignExpirySeconds) { this.presignExpirySeconds = presignExpirySeconds; }
}
