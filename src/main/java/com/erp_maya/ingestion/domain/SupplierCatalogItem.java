package com.erp_maya.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "supplier_catalog_items")
public class SupplierCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "supplier_id", nullable = false) private Long supplierId;
    @Column(name = "supplier_website_id", nullable = false) private Long supplierWebsiteId;
    @Column(name = "product_id") private Long productId;
    @Column(name = "source_product_name", nullable = false) private String sourceProductName;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_category_path", columnDefinition = "jsonb")
    private List<String> sourceCategoryPath;
    @Column(name = "source_listing_url") private String sourceListingUrl;
    @Column(name = "product_url") private String productUrl;
    @Column(name = "supplier_sku") private String supplierSku;
    private String brand;
    @Column(name = "manufacturer_name") private String manufacturerName;
    @Column(name = "manufacturer_part_number") private String manufacturerPartNumber;
    private String gtin;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_attributes", columnDefinition = "jsonb")
    private Map<String, Object> sourceAttributes;
    @Column(name = "observed_unit") private String observedUnit;
    @Column(name = "observed_pack_size") private BigDecimal observedPackSize;
    @Column(name = "observed_currency", nullable = false) private String observedCurrency;
    @Column(name = "observed_unit_cost") private BigDecimal observedUnitCost;
    @CreationTimestamp @Column(name = "first_seen_at", updatable = false) private Instant firstSeenAt;
    @UpdateTimestamp @Column(name = "last_seen_at") private Instant lastSeenAt;
    @Column(nullable = false) private Boolean active = Boolean.TRUE;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Long getSupplierWebsiteId() { return supplierWebsiteId; }
    public void setSupplierWebsiteId(Long supplierWebsiteId) { this.supplierWebsiteId = supplierWebsiteId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSourceProductName() { return sourceProductName; }
    public void setSourceProductName(String sourceProductName) { this.sourceProductName = sourceProductName; }
    public List<String> getSourceCategoryPath() { return sourceCategoryPath; }
    public void setSourceCategoryPath(List<String> sourceCategoryPath) { this.sourceCategoryPath = sourceCategoryPath; }
    public String getSourceListingUrl() { return sourceListingUrl; }
    public void setSourceListingUrl(String sourceListingUrl) { this.sourceListingUrl = sourceListingUrl; }
    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
    public String getSupplierSku() { return supplierSku; }
    public void setSupplierSku(String supplierSku) { this.supplierSku = supplierSku; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getManufacturerName() { return manufacturerName; }
    public void setManufacturerName(String manufacturerName) { this.manufacturerName = manufacturerName; }
    public String getManufacturerPartNumber() { return manufacturerPartNumber; }
    public void setManufacturerPartNumber(String manufacturerPartNumber) { this.manufacturerPartNumber = manufacturerPartNumber; }
    public String getGtin() { return gtin; }
    public void setGtin(String gtin) { this.gtin = gtin; }
    public Map<String, Object> getSourceAttributes() { return sourceAttributes; }
    public void setSourceAttributes(Map<String, Object> sourceAttributes) { this.sourceAttributes = sourceAttributes; }
    public String getObservedUnit() { return observedUnit; }
    public void setObservedUnit(String observedUnit) { this.observedUnit = observedUnit; }
    public BigDecimal getObservedPackSize() { return observedPackSize; }
    public void setObservedPackSize(BigDecimal observedPackSize) { this.observedPackSize = observedPackSize; }
    public String getObservedCurrency() { return observedCurrency; }
    public void setObservedCurrency(String observedCurrency) { this.observedCurrency = observedCurrency; }
    public BigDecimal getObservedUnitCost() { return observedUnitCost; }
    public void setObservedUnitCost(BigDecimal observedUnitCost) { this.observedUnitCost = observedUnitCost; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
