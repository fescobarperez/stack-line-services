package com.erp_maya.authorization.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Umbral. `unit` = 'percent' (min/max en %, sin moneda) o 'amount' (en dinero,
 * con moneda obligatoria). `maxValue` nulo = banda abierta hacia arriba.
 */
@Entity
@Table(name = "authorization_rules")
public class AuthorizationRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(nullable = false)
    private String unit;

    @Column(name = "min_value", nullable = false)
    private BigDecimal minValue = BigDecimal.ZERO;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    private String currency;

    @Column(name = "level_id", nullable = false)
    private Long levelId;

    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Long getLevelId() { return levelId; }
    public void setLevelId(Long levelId) { this.levelId = levelId; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
