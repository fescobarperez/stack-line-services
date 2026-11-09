package com.erp_maya.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Diseño aprobado por el cliente (ficha + render). Ver 083_quote_designs.sql. */
@Entity
@Table(name = "quote_designs")
public class QuoteDesign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    /** La ficha de diseño en JSON (QuoteDesignDtos.Spec). */
    @Column(name = "spec_json", nullable = false)
    private String specJson;

    @Column(name = "render_number")
    private Integer renderNumber;

    @Column(name = "render_max")
    private Integer renderMax;

    @Column(name = "image_mime")
    private String imageMime;

    @Column(name = "image_data")
    private byte[] imageData;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public String getSpecJson() { return specJson; }
    public void setSpecJson(String specJson) { this.specJson = specJson; }
    public Integer getRenderNumber() { return renderNumber; }
    public void setRenderNumber(Integer renderNumber) { this.renderNumber = renderNumber; }
    public Integer getRenderMax() { return renderMax; }
    public void setRenderMax(Integer renderMax) { this.renderMax = renderMax; }
    public String getImageMime() { return imageMime; }
    public void setImageMime(String imageMime) { this.imageMime = imageMime; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
