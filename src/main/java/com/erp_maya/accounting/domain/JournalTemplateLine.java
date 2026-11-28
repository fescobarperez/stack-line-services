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

import java.util.Arrays;
import java.util.List;

/**
 * Línea de una plantilla de partida.
 *
 * La cuenta es fija o, con {@code usesDocumentAccount}, la que trae el
 * documento (la caja o el banco concreto de un cobro); en ese caso la cuenta
 * fija es el respaldo si el documento no trae ninguna. El monto es la suma de
 * uno o varios montos del caso de uso: sin fórmulas.
 */
@Entity
@Table(name = "journal_template_lines")
public class JournalTemplateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private JournalTemplate template;

    @Column(nullable = false)
    private Integer position;

    /** debit | credit */
    @Column(nullable = false)
    private String side;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "uses_document_account", nullable = false)
    private Boolean usesDocumentAccount = Boolean.FALSE;

    /** Claves de montos separadas por coma: "igss_laboral,igss_patronal". */
    @Column(nullable = false)
    private String amounts;

    /** Texto con variables {descripcion}, {ref}, {tercero}. */
    private String description;

    @Column(name = "use_cost_center", nullable = false)
    private Boolean useCostCenter = Boolean.TRUE;

    public List<String> amountKeys() {
        if (amounts == null || amounts.isBlank()) return List.of();
        return Arrays.stream(amounts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public JournalTemplate getTemplate() { return template; }
    public void setTemplate(JournalTemplate template) { this.template = template; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public Boolean getUsesDocumentAccount() { return usesDocumentAccount; }
    public void setUsesDocumentAccount(Boolean usesDocumentAccount) { this.usesDocumentAccount = usesDocumentAccount; }

    public String getAmounts() { return amounts; }
    public void setAmounts(String amounts) { this.amounts = amounts; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getUseCostCenter() { return useCostCenter; }
    public void setUseCostCenter(Boolean useCostCenter) { this.useCostCenter = useCostCenter; }
}
