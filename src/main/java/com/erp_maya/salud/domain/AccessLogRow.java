package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Bitácora de accesos con el nombre del usuario (vista, solo lectura). */
@Entity
@Immutable
@Table(name = "salud_v_access_log")
public class AccessLogRow {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    private String action;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    public Long getId() { return id; }

    public Long getCompanyId() { return companyId; }

    public Long getPatientId() { return patientId; }

    public Long getUserId() { return userId; }

    public String getUserName() { return userName; }

    public String getAction() { return action; }

    public Instant getOccurredAt() { return occurredAt; }
}
