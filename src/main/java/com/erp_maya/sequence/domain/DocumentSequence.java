package com.erp_maya.sequence.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Correlativo de un tipo de documento y serie. Ver la 050. */
@Entity
@Table(name = "document_sequences")
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(nullable = false)
    private String series = "A";

    @Column(nullable = false)
    private String prefix = "";

    /** El próximo número a entregar. Se incrementa al reservarlo. */
    @Column(name = "next_number", nullable = false)
    private Long nextNumber = 1L;

    @Column(nullable = false)
    private Short padding = 6;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public Long getNextNumber() { return nextNumber; }
    public void setNextNumber(Long nextNumber) { this.nextNumber = nextNumber; }
    public Short getPadding() { return padding; }
    public void setPadding(Short padding) { this.padding = padding; }
}
