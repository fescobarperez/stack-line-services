package com.erp_maya.authorization.domain;

import jakarta.persistence.*;

/** Nivel de autoridad, configurable por empresa. `rank` ordena: mayor = más autoridad. */
@Entity
@Table(name = "authorization_levels")
public class AuthorizationLevel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Short rank;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Short getRank() { return rank; }
    public void setRank(Short rank) { this.rank = rank; }
}
