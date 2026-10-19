package com.erp_maya.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Materia prima que lleva la plantilla; con choiceId solo si se escoge esa elección. */
@Entity
@Table(name = "template_components")
public class TemplateComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "choice_id")
    private Long choiceId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity_formula", nullable = false)
    private String quantityFormula;

    @Column(name = "waste_pct", nullable = false)
    private BigDecimal wastePct = BigDecimal.ZERO;

    @Column(name = "round_up", nullable = false)
    private Boolean roundUp = Boolean.FALSE;

    @Column(name = "group_name", nullable = false)
    private String groupName = "Materiales";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getChoiceId() { return choiceId; }
    public void setChoiceId(Long choiceId) { this.choiceId = choiceId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getQuantityFormula() { return quantityFormula; }
    public void setQuantityFormula(String quantityFormula) { this.quantityFormula = quantityFormula; }
    public BigDecimal getWastePct() { return wastePct; }
    public void setWastePct(BigDecimal wastePct) { this.wastePct = wastePct; }
    public Boolean getRoundUp() { return roundUp; }
    public void setRoundUp(Boolean roundUp) { this.roundUp = roundUp; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
