package com.erp_maya.transfer.domain;

import com.erp_maya.company.domain.Branch;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Traslado de stock entre sucursales. Doble FK a branches (origen/destino). */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "doc_number", nullable = false)
    private String docNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_branch_id", nullable = false)
    private Branch fromBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_branch_id", nullable = false)
    private Branch toBranch;

    private String transporter;

    @Column(name = "transfer_date")
    private LocalDate transferDate;

    private String status = "draft";

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addItem(TransferItem item) {
        item.setTransfer(this);
        item.setCompanyId(this.companyId);
        this.items.add(item);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getDocNumber() { return docNumber; }
    public void setDocNumber(String docNumber) { this.docNumber = docNumber; }

    public Branch getFromBranch() { return fromBranch; }
    public void setFromBranch(Branch fromBranch) { this.fromBranch = fromBranch; }

    public Branch getToBranch() { return toBranch; }
    public void setToBranch(Branch toBranch) { this.toBranch = toBranch; }

    public String getTransporter() { return transporter; }
    public void setTransporter(String transporter) { this.transporter = transporter; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<TransferItem> getItems() { return items; }
    public void setItems(List<TransferItem> items) { this.items = items; }
}
