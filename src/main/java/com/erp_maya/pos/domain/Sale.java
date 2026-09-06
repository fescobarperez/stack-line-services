package com.erp_maya.pos.domain;

import com.erp_maya.company.domain.Branch;
import com.erp_maya.partner.domain.Client;
import com.erp_maya.security.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Encabezado de la venta/factura. Sus renglones van en {@link SaleItem}. */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "doc_number", nullable = false)
    private String docNumber;

    /** DTE de SAT: FACT, NCRE, NDEB… Ver el comentario de la columna en la 045. */
    @Column(name = "doc_type", nullable = false)
    private String docType = "FACT";

    @Column(name = "series")
    private String series;

    /** Documento que modifica esta NC/ND. SAT exige la referencia al original. */
    @Column(name = "related_sale_id")
    private Long relatedSaleId;

    @Column(name = "reason")
    private String reason;

    /** Vendida al fiado: genera CxC y no entra al arqueo de caja. */
    @Column(name = "is_credit", nullable = false)
    private boolean credit = false;

    /**
     * Total con signo por tipo, generado por la base (ver la 045). Solo lectura:
     * escribirlo desde aquí haría fallar el INSERT contra una columna GENERATED.
     * `@Generated` hace que Hibernate lo relea tras insertar o actualizar; sin
     * eso la respuesta del POS saldría con el campo en null recién creada.
     */
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "signed_total", insertable = false, updatable = false)
    private BigDecimal signedTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    @Column(name = "sale_date", nullable = false)
    private Instant saleDate;

    @Column(name = "payment_method")
    private String paymentMethod;

    private BigDecimal subtotal = BigDecimal.ZERO;

    private BigDecimal tax = BigDecimal.ZERO;

    /** Tasa aplicada, congelada: si mañana cambia, este documento no. */
    @Column(name = "tax_rate", nullable = false)
    private BigDecimal taxRate = new BigDecimal("12");

    private BigDecimal total = BigDecimal.ZERO;

    /** Descuento manual sobre el total y su % efectivo (lo que se autoriza). */
    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "discount_percent", nullable = false)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** Proyecto contra el que se emitió la venta, si pertenece a uno. */
    @Column(name = "project_id")
    private Long projectId;

    /** La autorización que permitió el descuento manual, si hizo falta. */
    @Column(name = "authorization_id")
    private Long authorizationId;

    private String status = "paid";

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addItem(SaleItem item) {
        item.setSale(this);
        item.setCompanyId(this.companyId);
        this.items.add(item);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }

    public Long getRelatedSaleId() { return relatedSaleId; }
    public void setRelatedSaleId(Long relatedSaleId) { this.relatedSaleId = relatedSaleId; }

    public boolean isCredit() { return credit; }
    public void setCredit(boolean credit) { this.credit = credit; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getSignedTotal() { return signedTotal; }

    public String getDocNumber() { return docNumber; }
    public void setDocNumber(String docNumber) { this.docNumber = docNumber; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }

    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    public CashRegister getCashRegister() { return cashRegister; }
    public void setCashRegister(CashRegister cashRegister) { this.cashRegister = cashRegister; }

    public Instant getSaleDate() { return saleDate; }
    public void setSaleDate(Instant saleDate) { this.saleDate = saleDate; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items; }
}
