package com.erp_maya.returns.domain;

import com.erp_maya.partner.domain.Client;
import com.erp_maya.pos.domain.Sale;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Nota de crédito por devolución. */
@Entity
@Table(name = "credit_notes")
public class CreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "doc_number", nullable = false)
    private String docNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "return_date")
    private LocalDate returnDate;

    private String reason;

    @Column(name = "refund_method")
    private String refundMethod;

    private BigDecimal total = BigDecimal.ZERO;

    private String status = "issued";

    /** anulacion | devolucion | descuento. */
    @Column(name = "note_type")
    private String noteType = "devolucion";

    @Column(name = "ticket_ref")
    private String ticketRef;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "client_nit")
    private String clientNit;

    private String cashier;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "fel_status")
    private String felStatus = "pendiente";

    @Column(name = "fel_uuid")
    private String felUuid;

    @Column(name = "fel_error")
    private String felError;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CreditNoteItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addItem(CreditNoteItem item) {
        item.setCreditNote(this);
        item.setCompanyId(this.companyId);
        this.items.add(item);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getDocNumber() { return docNumber; }
    public void setDocNumber(String docNumber) { this.docNumber = docNumber; }

    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<CreditNoteItem> getItems() { return items; }
    public void setItems(List<CreditNoteItem> items) { this.items = items; }

    public String getNoteType() { return noteType; }
    public void setNoteType(String noteType) { this.noteType = noteType; }

    public String getTicketRef() { return ticketRef; }
    public void setTicketRef(String ticketRef) { this.ticketRef = ticketRef; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientNit() { return clientNit; }
    public void setClientNit(String clientNit) { this.clientNit = clientNit; }

    public String getCashier() { return cashier; }
    public void setCashier(String cashier) { this.cashier = cashier; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public String getFelStatus() { return felStatus; }
    public void setFelStatus(String felStatus) { this.felStatus = felStatus; }

    public String getFelUuid() { return felUuid; }
    public void setFelUuid(String felUuid) { this.felUuid = felUuid; }

    public String getFelError() { return felError; }
    public void setFelError(String felError) { this.felError = felError; }
}
