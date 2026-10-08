package com.erp_maya.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que el cliente pidió cambiar de una cotización que ya tomó un vendedor.
 *
 * El asistente no toca la cotización: deja la solicitud y el vendedor la
 * acepta, la ajusta o la rechaza desde el panel. Al aplicar, el ERP hace los
 * cambios y avisa al cliente.
 */
@Entity
@Table(name = "quote_change_requests")
public class QuoteChangeRequest {

    public static final String PENDIENTE = "pendiente";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    /** agregar | quitar | cantidad | descuento | condiciones | otro */
    @Column(nullable = false)
    private String kind;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    private BigDecimal quantity;

    @Column(name = "discount_pct")
    private BigDecimal discountPct;

    private String detail;

    @Column(nullable = false)
    private String status = PENDIENTE;

    private String response;

    @Column(name = "adjusted_quantity")
    private BigDecimal adjustedQuantity;

    @Column(name = "adjusted_discount_pct")
    private BigDecimal adjustedDiscountPct;

    /** Motivo del catálogo cuando se rechaza o se ajusta. */
    @Column(name = "reason_code")
    private String reasonCode;

    /** Una consulta apunta a la solicitud sobre la que pregunta el cliente. */
    @Column(name = "parent_id")
    private Long parentId;

    private String source;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public boolean pendiente() { return PENDIENTE.equals(status); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal discountPct) { this.discountPct = discountPct; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public BigDecimal getAdjustedQuantity() { return adjustedQuantity; }
    public void setAdjustedQuantity(BigDecimal adjustedQuantity) { this.adjustedQuantity = adjustedQuantity; }
    public BigDecimal getAdjustedDiscountPct() { return adjustedDiscountPct; }
    public void setAdjustedDiscountPct(BigDecimal adjustedDiscountPct) { this.adjustedDiscountPct = adjustedDiscountPct; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
