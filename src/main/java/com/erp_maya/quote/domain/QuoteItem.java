package com.erp_maya.quote.domain;

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

/** Renglón de la cotización. */
@Entity
@Table(name = "quote_items")
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private BigDecimal quantity;

    /** Nombre libre del renglón (servicio/insumo sin producto de catálogo). */
    @Column(name = "item_name")
    private String itemName;

    private String uom;

    /** Descripción de UN nivel que ve el cliente en el PDF. Default = nombre de carpeta. */
    private String description;

    /** Carpeta del árbol de materiales de la que salió la línea, si aplica. */
    @Column(name = "source_group_id")
    private Long sourceGroupId;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    /** La medida que pide el cliente; nace igual a la del catálogo. */
    private BigDecimal height;
    private BigDecimal width;
    private BigDecimal depth;

    /** Copia de la medida del catálogo al cotizar: contra qué se compara. */
    @Column(name = "base_height")
    private BigDecimal baseHeight;
    @Column(name = "base_width")
    private BigDecimal baseWidth;
    @Column(name = "base_depth")
    private BigDecimal baseDepth;

    /** Con precio global: la línea interna (p. ej. materiales) no sale en el PDF. */
    @Column(name = "show_to_client", nullable = false)
    private Boolean showToClient = Boolean.TRUE;

    /** null (catálogo/asesor) · 'calculated' (por medida) · 'review' (fuera de tolerancia). */
    @Column(name = "size_pricing")
    private String sizePricing;

    /** Plantilla con la que se calculó la línea (084); null = línea normal. */
    @Column(name = "template_id")
    private Long templateId;

    /** Parámetros y elecciones del cálculo en JSON, para recalcularla. */
    @Column(name = "template_config")
    private String templateConfig;

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getSourceGroupId() { return sourceGroupId; }
    public void setSourceGroupId(Long sourceGroupId) { this.sourceGroupId = sourceGroupId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Quote getQuote() { return quote; }
    public void setQuote(Quote quote) { this.quote = quote; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }
    public BigDecimal getDepth() { return depth; }
    public void setDepth(BigDecimal depth) { this.depth = depth; }
    public BigDecimal getBaseHeight() { return baseHeight; }
    public void setBaseHeight(BigDecimal baseHeight) { this.baseHeight = baseHeight; }
    public BigDecimal getBaseWidth() { return baseWidth; }
    public void setBaseWidth(BigDecimal baseWidth) { this.baseWidth = baseWidth; }
    public BigDecimal getBaseDepth() { return baseDepth; }
    public void setBaseDepth(BigDecimal baseDepth) { this.baseDepth = baseDepth; }
    public String getSizePricing() { return sizePricing; }
    public void setSizePricing(String sizePricing) { this.sizePricing = sizePricing; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateConfig() { return templateConfig; }
    public void setTemplateConfig(String templateConfig) { this.templateConfig = templateConfig; }
    public Boolean getShowToClient() { return showToClient; }
    public void setShowToClient(Boolean showToClient) { this.showToClient = showToClient; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
