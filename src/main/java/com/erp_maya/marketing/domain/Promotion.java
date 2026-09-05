package com.erp_maya.marketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Regla de promoción (2x1, 3x2, combo, % off). target polimórfico por texto. */
@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "promo_type")
    private String promoType;

    private String target;

    private String valid;

    private String status = "active";

    private BigDecimal value;

    private String category;

    private String product;

    @Column(name = "client_type")
    private String clientType;

    private String branches;

    private String days;

    @Column(name = "hora_inicio")
    private String horaInicio;

    @Column(name = "hora_fin")
    private String horaFin;

    @Column(name = "min_compra")
    private BigDecimal minCompra;

    @Column(name = "nxm_n")
    private Integer nxmN;

    @Column(name = "nxm_m")
    private Integer nxmM;

    @Column(name = "date_start")
    private LocalDate dateStart;

    @Column(name = "date_end")
    private LocalDate dateEnd;

    @Column(columnDefinition = "text")
    private String description;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPromoType() { return promoType; }
    public void setPromoType(String promoType) { this.promoType = promoType; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getValid() { return valid; }
    public void setValid(String valid) { this.valid = valid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }

    public String getBranches() { return branches; }
    public void setBranches(String branches) { this.branches = branches; }

    public String getDays() { return days; }
    public void setDays(String days) { this.days = days; }

    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = horaInicio; }

    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = horaFin; }

    public BigDecimal getMinCompra() { return minCompra; }
    public void setMinCompra(BigDecimal minCompra) { this.minCompra = minCompra; }

    public Integer getNxmN() { return nxmN; }
    public void setNxmN(Integer nxmN) { this.nxmN = nxmN; }

    public Integer getNxmM() { return nxmM; }
    public void setNxmM(Integer nxmM) { this.nxmM = nxmM; }

    public LocalDate getDateStart() { return dateStart; }
    public void setDateStart(LocalDate dateStart) { this.dateStart = dateStart; }

    public LocalDate getDateEnd() { return dateEnd; }
    public void setDateEnd(LocalDate dateEnd) { this.dateEnd = dateEnd; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
