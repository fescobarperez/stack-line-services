package com.erp_maya.accounting.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla de partida: qué cuentas mueve, en qué lado y con qué monto del
 * caso de uso. La empresa la define en Contabilidad → Partidas automáticas y la
 * asocia a un caso de uso con {@link PostingRule}.
 */
@Entity
@Table(name = "journal_templates")
public class JournalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Código de {@code PostingCase}: decide qué montos pueden usar las líneas. */
    @Column(name = "use_case", nullable = false)
    private String useCase;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    /** Sube en cada edición; la partida guarda con qué versión nació. */
    @Column(nullable = false)
    private Integer version = 1;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<JournalTemplateLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addLine(JournalTemplateLine line) {
        line.setTemplate(this);
        this.lines.add(line);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getUseCase() { return useCase; }
    public void setUseCase(String useCase) { this.useCase = useCase; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public List<JournalTemplateLine> getLines() { return lines; }
    public void setLines(List<JournalTemplateLine> lines) { this.lines = lines; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
