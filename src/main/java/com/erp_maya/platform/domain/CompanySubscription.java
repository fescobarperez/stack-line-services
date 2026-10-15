package com.erp_maya.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Plan de una empresa y sus excepciones (company_subscriptions). */
@Entity
@Table(name = "company_subscriptions")
public class CompanySubscription {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "price_override")
    private BigDecimal priceOverride;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_modules", columnDefinition = "jsonb", nullable = false)
    private List<String> enabledModules = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disabled_modules", columnDefinition = "jsonb", nullable = false)
    private List<String> disabledModules = new ArrayList<>();

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_branches")
    private Integer maxBranches;

    @Column(name = "max_fel_per_month")
    private Integer maxFelPerMonth;

    @Column(name = "max_ai_messages_per_month")
    private Integer maxAiMessagesPerMonth;

    @Column(name = "updated_by")
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
    public List<String> getEnabledModules() { return enabledModules; }
    public void setEnabledModules(List<String> v) { this.enabledModules = v; }
    public List<String> getDisabledModules() { return disabledModules; }
    public void setDisabledModules(List<String> v) { this.disabledModules = v; }
    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public Integer getMaxBranches() { return maxBranches; }
    public void setMaxBranches(Integer maxBranches) { this.maxBranches = maxBranches; }
    public Integer getMaxFelPerMonth() { return maxFelPerMonth; }
    public void setMaxFelPerMonth(Integer maxFelPerMonth) { this.maxFelPerMonth = maxFelPerMonth; }
    public Integer getMaxAiMessagesPerMonth() { return maxAiMessagesPerMonth; }
    public void setMaxAiMessagesPerMonth(Integer v) { this.maxAiMessagesPerMonth = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
