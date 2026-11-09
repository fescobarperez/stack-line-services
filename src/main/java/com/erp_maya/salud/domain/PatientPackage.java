package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Paquete vendido a un paciente: cada cita de su tipo descuenta una sesión (ver 093). */
@Entity
@Table(name = "salud_patient_packages")
public class PatientPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "package_id")
    private Long packageId;

    @Column(nullable = false)
    private String name;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(name = "sessions_total", nullable = false)
    private int sessionsTotal;

    @Column(name = "sessions_used", nullable = false)
    private int sessionsUsed;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(nullable = false)
    private String status = "activo";

    @Column(name = "created_by")
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }

    public int getSessionsTotal() { return sessionsTotal; }
    public void setSessionsTotal(int sessionsTotal) { this.sessionsTotal = sessionsTotal; }

    public int getSessionsUsed() { return sessionsUsed; }
    public void setSessionsUsed(int sessionsUsed) { this.sessionsUsed = sessionsUsed; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public Instant getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(Instant purchasedAt) { this.purchasedAt = purchasedAt; }

    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
