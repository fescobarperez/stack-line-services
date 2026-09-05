package com.erp_maya.authorization.domain;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Solicitud de autorización.
 *
 * `payload` es JSON que el motor NO interpreta: lo guarda y lo devuelve al
 * resolverse, para que el módulo que pidió sepa qué continuar. Es lo que hace
 * que el mismo motor sirva para un descuento, una nota de crédito o un pago.
 */
@Entity
@Table(name = "authorization_requests")
public class AuthorizationRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "branch_id")
    private Long branchId;

    private BigDecimal amount;

    private String currency;

    private BigDecimal percent;

    @TypeDef(type = DataType.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "resolution_mode", nullable = false)
    private String resolutionMode;

    private String reference;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolutionMode() { return resolutionMode; }
    public void setResolutionMode(String resolutionMode) { this.resolutionMode = resolutionMode; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
