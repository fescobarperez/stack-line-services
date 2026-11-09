package com.erp_maya.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Plan comercial: módulos incluidos, límites (null = ilimitado) y precio mensual (Q). */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> modules = new ArrayList<>();

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_branches")
    private Integer maxBranches;

    @Column(name = "max_fel_per_month")
    private Integer maxFelPerMonth;

    @Column(name = "max_ai_messages_per_month")
    private Integer maxAiMessagesPerMonth;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public List<String> getModules() { return modules; }
    public void setModules(List<String> modules) { this.modules = modules; }
    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public Integer getMaxBranches() { return maxBranches; }
    public void setMaxBranches(Integer maxBranches) { this.maxBranches = maxBranches; }
    public Integer getMaxFelPerMonth() { return maxFelPerMonth; }
    public void setMaxFelPerMonth(Integer maxFelPerMonth) { this.maxFelPerMonth = maxFelPerMonth; }
    public Integer getMaxAiMessagesPerMonth() { return maxAiMessagesPerMonth; }
    public void setMaxAiMessagesPerMonth(Integer v) { this.maxAiMessagesPerMonth = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
