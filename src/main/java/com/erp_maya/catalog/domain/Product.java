package com.erp_maya.catalog.domain;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private BigDecimal price;

    private BigDecimal cost;

    @Column(name = "avg_cost")
    private BigDecimal avgCost;

    private String unit;

    /** Cómo se compra, si no coincide con cómo se almacena: 'Paquete', 'Caja'. */
    @Column(name = "purchase_unit")
    private String purchaseUnit;

    /** Unidades de existencia por unidad de compra. 1 = se compra como se guarda. */
    @Column(name = "purchase_factor", nullable = false)
    private BigDecimal purchaseFactor = BigDecimal.ONE;

    @Column(name = "min_stock")
    private BigDecimal minStock;

    /** Medida estándar del catálogo (opcional): base para cotizar a medida. */
    private BigDecimal height;
    private BigDecimal width;
    private BigDecimal depth;

    @Column(name = "dimension_unit")
    private String dimensionUnit;

    /**
     * sellable · raw_material · service. Solo `sellable` se ofrece en el POS;
     * la materia prima se consume y el servicio (mano de obra) no lleva stock.
     */
    @Column(name = "item_type", nullable = false)
    private String itemType = "sellable";

    @Column(name = "tracks_stock", nullable = false)
    private Boolean tracksStock = Boolean.TRUE;

    private String status;

    /** manual · assistant (lo propuso el asistente de WhatsApp). */
    @Column(nullable = false)
    private String origin = ORIGIN_MANUAL;

    /** NULL = producto normal · pending · discarded. Ver 082_product_review.sql. */
    @Column(name = "review_status")
    private String reviewStatus;

    /** Lo que el asistente sabe del pedido, para quien lo revisa. */
    @Column(name = "review_notes")
    private String reviewNotes;

    /** Versión de la foto (ver product_images); nula si no tiene. */
    @Column(name = "image_updated_at")
    private Instant imageUpdatedAt;

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

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }

    public String getPurchaseUnit() { return purchaseUnit; }
    public void setPurchaseUnit(String purchaseUnit) { this.purchaseUnit = purchaseUnit; }

    public BigDecimal getPurchaseFactor() { return purchaseFactor; }
    public void setPurchaseFactor(BigDecimal purchaseFactor) { this.purchaseFactor = purchaseFactor; }

    /** Nunca nulo ni cero: sin factor válido, comprar es guardar tal cual. */
    public BigDecimal purchaseFactorOrOne() {
        return purchaseFactor != null && purchaseFactor.signum() > 0 ? purchaseFactor : BigDecimal.ONE;
    }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }
    public BigDecimal getDepth() { return depth; }
    public void setDepth(BigDecimal depth) { this.depth = depth; }
    public String getDimensionUnit() { return dimensionUnit; }
    public void setDimensionUnit(String dimensionUnit) { this.dimensionUnit = dimensionUnit; }
    public BigDecimal getMinStock() { return minStock; }
    public void setMinStock(BigDecimal minStock) { this.minStock = minStock; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Boolean getTracksStock() { return tracksStock; }
    public void setTracksStock(Boolean tracksStock) { this.tracksStock = tracksStock; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    /** Propuesto por el asistente y todavía sin revisar. */
    public boolean isPendingReview() { return REVIEW_PENDING.equals(reviewStatus); }

    public Instant getImageUpdatedAt() { return imageUpdatedAt; }
    public void setImageUpdatedAt(Instant imageUpdatedAt) { this.imageUpdatedAt = imageUpdatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static final String ORIGIN_MANUAL = "manual";
    public static final String ORIGIN_ASSISTANT = "assistant";
    public static final String REVIEW_PENDING = "pending";
    public static final String REVIEW_DISCARDED = "discarded";
}
