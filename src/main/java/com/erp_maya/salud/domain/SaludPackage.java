package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Paquete del catálogo: N sesiones de un tipo de cita a un precio (ver 093). */
@Entity
@Table(name = "salud_packages")
public class SaludPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(nullable = false)
    private int sessions;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "valid_days")
    private Integer validDays;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }

    public int getSessions() { return sessions; }
    public void setSessions(int sessions) { this.sessions = sessions; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer validDays) { this.validDays = validDays; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
