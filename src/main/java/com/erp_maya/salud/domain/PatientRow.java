package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

/** Fila del listado de pacientes (vista salud_v_patients, solo lectura). */
@Entity
@Immutable
@Table(name = "salud_v_patients")
public class PatientRow {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "record_number")
    private String recordNumber;

    @Column(name = "doc_type")
    private String docType;

    @Column(name = "doc_number")
    private String docNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String sex;

    private String status;

    @Column(name = "primary_professional_id")
    private Long primaryProfessionalId;

    @Column(name = "created_at")
    private Instant createdAt;

    private String name;

    private String nit;

    private String phone;

    private String email;

    @Column(name = "phone_digits")
    private String phoneDigits;

    public Long getId() { return id; }

    public Long getCompanyId() { return companyId; }

    public String getRecordNumber() { return recordNumber; }

    public String getDocType() { return docType; }

    public String getDocNumber() { return docNumber; }

    public LocalDate getBirthDate() { return birthDate; }

    public String getSex() { return sex; }

    public String getStatus() { return status; }

    public Long getPrimaryProfessionalId() { return primaryProfessionalId; }

    public Instant getCreatedAt() { return createdAt; }

    public String getName() { return name; }

    public String getNit() { return nit; }

    public String getPhone() { return phone; }

    public String getEmail() { return email; }

    public String getPhoneDigits() { return phoneDigits; }
}
