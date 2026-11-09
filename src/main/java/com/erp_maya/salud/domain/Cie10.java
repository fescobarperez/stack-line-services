package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Diagnóstico del catálogo CIE-10 (común a todas las empresas). */
@Entity
@Immutable
@Table(name = "salud_cie10")
public class Cie10 {

    @Id
    private String code;

    @Column(nullable = false)
    private String description;

    @Column(name = "search_text", nullable = false)
    private String searchText;

    public String getCode() { return code; }

    public String getDescription() { return description; }

    public String getSearchText() { return searchText; }
}
