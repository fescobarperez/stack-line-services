package com.erp_maya.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Categoría de gasto de cotización.
 *
 * Sustituye al texto libre que había en quote_charges.category: sin catálogo
 * no se podía sumar por concepto ni comparar la estructura de costo entre
 * cotizaciones.
 *
 * `operating` marca las que suman al bloque de Gastos Operativos; `protectedRow`,
 * las que el mantenimiento no deja borrar porque el cálculo depende de ellas.
 */
@Entity
@Table(name = "charge_categories")
public class ChargeCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_operating", nullable = false)
    private Boolean operating = Boolean.FALSE;

    /** El nombre de la columna es `protected`, palabra reservada en Java. */
    @Column(name = "protected", nullable = false)
    private Boolean protectedRow = Boolean.FALSE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private String status = "active";

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
    public Boolean getOperating() { return operating; }
    public void setOperating(Boolean operating) { this.operating = operating; }
    public Boolean getProtectedRow() { return protectedRow; }
    public void setProtectedRow(Boolean protectedRow) { this.protectedRow = protectedRow; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
