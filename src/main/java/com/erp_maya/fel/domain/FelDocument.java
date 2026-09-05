package com.erp_maya.fel.domain;

import com.erp_maya.pos.domain.Sale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Documento tributario electrónico (DTE) emitido a SAT (FEL). */
@Entity
@Table(name = "fel_documents")
public class FelDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(name = "dte_type", nullable = false)
    private String dteType;

    private String series;

    private String number;

    private String uuid;

    @Column(name = "authorization_number")
    private String authorizationNumber;

    @Column(name = "receptor_name")
    private String receptorName;

    @Column(name = "receptor_nit")
    private String receptorNit;

    @Column(name = "taxable_amount")
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "exempt_amount")
    private BigDecimal exemptAmount = BigDecimal.ZERO;

    private BigDecimal tax = BigDecimal.ZERO;

    private BigDecimal total = BigDecimal.ZERO;

    private String status = "pending";

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "certified_at")
    private Instant certifiedAt;

    private String xml;

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

    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }

    public String getDteType() { return dteType; }
    public void setDteType(String dteType) { this.dteType = dteType; }

    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getAuthorizationNumber() { return authorizationNumber; }
    public void setAuthorizationNumber(String authorizationNumber) { this.authorizationNumber = authorizationNumber; }

    public String getReceptorName() { return receptorName; }
    public void setReceptorName(String receptorName) { this.receptorName = receptorName; }

    public String getReceptorNit() { return receptorNit; }
    public void setReceptorNit(String receptorNit) { this.receptorNit = receptorNit; }

    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }

    public BigDecimal getExemptAmount() { return exemptAmount; }
    public void setExemptAmount(BigDecimal exemptAmount) { this.exemptAmount = exemptAmount; }

    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getCertifiedAt() { return certifiedAt; }
    public void setCertifiedAt(Instant certifiedAt) { this.certifiedAt = certifiedAt; }

    public String getXml() { return xml; }
    public void setXml(String xml) { this.xml = xml; }
}
