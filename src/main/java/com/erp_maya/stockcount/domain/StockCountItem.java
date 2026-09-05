package com.erp_maya.stockcount.domain;

import com.erp_maya.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Renglón del conteo: cantidad de sistema vs contada y su diferencia. */
@Entity
@Table(name = "stock_count_items")
public class StockCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "system_qty", nullable = false)
    private BigDecimal systemQty = BigDecimal.ZERO;

    @Column(name = "counted_qty")
    private BigDecimal countedQty;

    private BigDecimal difference;

    @Column(name = "line_notes")
    private String lineNotes;

    public String getLineNotes() { return lineNotes; }
    public void setLineNotes(String lineNotes) { this.lineNotes = lineNotes; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public StockCount getStockCount() { return stockCount; }
    public void setStockCount(StockCount stockCount) { this.stockCount = stockCount; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public BigDecimal getSystemQty() { return systemQty; }
    public void setSystemQty(BigDecimal systemQty) { this.systemQty = systemQty; }

    public BigDecimal getCountedQty() { return countedQty; }
    public void setCountedQty(BigDecimal countedQty) { this.countedQty = countedQty; }

    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }
}
