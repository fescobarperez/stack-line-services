package com.erp_maya.uom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Unidad de medida (unidad, caja, docena, kg…). */
@Entity
@Table(name = "units_of_measure")
public class UnitOfMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String symbol;

    @Column(name = "uom_type")
    private String uomType;

    @Column(name = "is_base", nullable = false)
    private Boolean isBase = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getUomType() { return uomType; }
    public void setUomType(String uomType) { this.uomType = uomType; }

    public Boolean getIsBase() { return isBase; }
    public void setIsBase(Boolean isBase) { this.isBase = isBase; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
