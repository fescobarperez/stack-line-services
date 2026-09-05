package com.erp_maya.authorization.domain;

import jakarta.persistence.*;

/** Qué se puede autorizar. `resolutionMode`: pin (en sitio) · tray (bandeja) · both. */
@Entity
@Table(name = "authorization_types")
public class AuthorizationType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "resolution_mode", nullable = false)
    private String resolutionMode = "both";

    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getResolutionMode() { return resolutionMode; }
    public void setResolutionMode(String resolutionMode) { this.resolutionMode = resolutionMode; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
