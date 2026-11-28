package com.erp_maya.accounting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Qué plantilla se ejecuta automáticamente en un caso de uso (p. ej. la venta
 * POS de contado). Una por empresa y caso de uso. Sin una activa, la operación
 * se bloquea: una venta sin partida descuadra el libro en silencio.
 */
@Entity
@Table(name = "posting_rules")
public class PostingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "use_case", nullable = false)
    private String useCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private JournalTemplate template;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

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

    public String getUseCase() { return useCase; }
    public void setUseCase(String useCase) { this.useCase = useCase; }

    public JournalTemplate getTemplate() { return template; }
    public void setTemplate(JournalTemplate template) { this.template = template; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getUpdatedAt() { return updatedAt; }
}
