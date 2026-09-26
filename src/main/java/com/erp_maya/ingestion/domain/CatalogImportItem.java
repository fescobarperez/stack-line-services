package com.erp_maya.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "catalog_import_items")
public class CatalogImportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_payload", columnDefinition = "jsonb")
    private Map<String, Object> normalizedPayload;

    @Column(nullable = false)
    private String action = "REVIEW";

    @Column(name = "target_product_id")
    private Long targetProductId;

    @Column(name = "target_product_supplier_id")
    private Long targetProductSupplierId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confidence", columnDefinition = "jsonb")
    private Map<String, Object> confidence;

    @Column(name = "decision_reason")
    private String decisionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alternative_candidates", columnDefinition = "jsonb")
    private List<Map<String, Object>> alternativeCandidates;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "error_message")
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public Map<String, Object> getRawPayload() { return rawPayload; }
    public void setRawPayload(Map<String, Object> rawPayload) { this.rawPayload = rawPayload; }
    public Map<String, Object> getNormalizedPayload() { return normalizedPayload; }
    public void setNormalizedPayload(Map<String, Object> normalizedPayload) { this.normalizedPayload = normalizedPayload; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getTargetProductId() { return targetProductId; }
    public void setTargetProductId(Long targetProductId) { this.targetProductId = targetProductId; }
    public Long getTargetProductSupplierId() { return targetProductSupplierId; }
    public void setTargetProductSupplierId(Long targetProductSupplierId) { this.targetProductSupplierId = targetProductSupplierId; }
    public Map<String, Object> getConfidence() { return confidence; }
    public void setConfidence(Map<String, Object> confidence) { this.confidence = confidence; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }
    public List<Map<String, Object>> getAlternativeCandidates() { return alternativeCandidates; }
    public void setAlternativeCandidates(List<Map<String, Object>> alternativeCandidates) { this.alternativeCandidates = alternativeCandidates; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
