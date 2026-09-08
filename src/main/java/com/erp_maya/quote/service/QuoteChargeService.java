package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.quote.domain.QuoteCharge;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.quote.repository.QuoteChargeRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.settings.service.TaxService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Gastos/cargos de la cotización y el cálculo de su total.
 *
 * El gasto de materiales es DERIVADO (Σ costo de los materiales de la
 * cotización) y no se persiste. Los cargos manuales (fixed/percent) sí. Los
 * percent se aplican sobre el subtotal de costo = materiales + fixed.
 */
@Singleton
public class QuoteChargeService {

    private final QuoteChargeRepository charges;
    private final QuoteRepository quotes;
    private final Materials materials;
    private final TenantContext tenant;
    private final TaxService taxService;

    public QuoteChargeService(QuoteChargeRepository charges, QuoteRepository quotes,
                              Materials materials, TenantContext tenant, TaxService taxService) {
        this.charges = charges;
        this.quotes = quotes;
        this.materials = materials;
        this.tenant = tenant;
        this.taxService = taxService;
    }

    @Transactional
    public QuoteChargeDtos.Summary getSummary(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        return recompute(quoteId, companyId);
    }

    @Transactional
    public QuoteChargeDtos.Summary addCharge(Long quoteId, QuoteChargeDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        String calc = req.calcType() != null ? req.calcType().trim().toLowerCase() : "fixed";
        if (!calc.equals("fixed") && !calc.equals("percent")) {
            throw new IllegalStateException("El tipo de cargo debe ser 'fixed' o 'percent'.");
        }
        QuoteCharge c = new QuoteCharge();
        c.setCompanyId(companyId);
        c.setQuoteId(quoteId);
        c.setCategory(req.category());
        c.setDescription(req.description());
        c.setCalcType(calc);
        c.setValue(req.value() != null ? req.value() : BigDecimal.ZERO);
        c.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        charges.save(c);
        return recompute(quoteId, companyId);
    }

    @Transactional
    public QuoteChargeDtos.Summary deleteCharge(Long quoteId, Long chargeId) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        charges.findByIdAndCompanyId(chargeId, companyId)
                .filter(c -> c.getQuoteId().equals(quoteId))
                .ifPresent(charges::delete);
        return recompute(quoteId, companyId);
    }

    /**
     * Recalcula el resumen y congela computed_amount en cada cargo.
     * Orden: materiales (derivado) + fixed = subtotal de costo; luego los
     * percent sobre ese subtotal; total = subtotal + percent.
     */
    private QuoteChargeDtos.Summary recompute(Long quoteId, Long companyId) {
        var quote = quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        BigDecimal materialsCost = materials.findByCompanyIdAndQuoteId(companyId, quoteId).stream()
                .map(this::materialAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Cotizaciones antiguas o no ligadas a proyecto usan sus líneas como
        // base operativa para no quedar con un total Q0 al recalcular.
        if (materialsCost.signum() == 0) {
            materialsCost = quote.getItems().stream()
                    .map(i -> i.getLineTotal() == null ? BigDecimal.ZERO : i.getLineTotal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        List<QuoteCharge> rows = charges.findByCompanyIdAndQuoteIdOrderBySortOrderAsc(companyId, quoteId);
        BigDecimal fixedTotal = BigDecimal.ZERO;
        for (QuoteCharge c : rows) {
            if ("fixed".equals(c.getCalcType())) {
                BigDecimal amt = money(c.getValue());
                c.setComputedAmount(amt);
                fixedTotal = fixedTotal.add(amt);
            }
        }
        BigDecimal subtotalCost = money(materialsCost.add(fixedTotal));
        BigDecimal percentTotal = BigDecimal.ZERO;
        for (QuoteCharge c : rows) {
            if ("percent".equals(c.getCalcType())) {
                BigDecimal amt = money(subtotalCost.multiply(c.getValue())
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                c.setComputedAmount(amt);
                percentTotal = percentTotal.add(amt);
            }
        }
        rows.forEach(charges::update);

        BigDecimal operatingCost = money(subtotalCost.add(percentTotal));
        String profitCalcType = "percent".equals(quote.getProfitCalcType()) ? "percent" : "fixed";
        BigDecimal profitValue = money(quote.getProfitValue());
        BigDecimal profitAmount = "percent".equals(profitCalcType)
                ? money(operatingCost.multiply(profitValue).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                : profitValue;
        BigDecimal taxableSubtotal = money(operatingCost.add(profitAmount));
        BigDecimal taxRate = quote.getTaxRate() == null ? taxService.rate() : quote.getTaxRate();
        BigDecimal tax = money(taxableSubtotal.multiply(taxRate)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal total = money(taxableSubtotal.add(tax));

        quote.setProfitCalcType(profitCalcType);
        quote.setProfitValue(profitValue);
        quote.setProfitAmount(profitAmount);
        quote.setSubtotal(taxableSubtotal);
        quote.setTax(tax);
        quote.setTotal(total);
        quotes.update(quote);

        List<QuoteChargeDtos.ChargeResponse> out = rows.stream()
                .map(c -> new QuoteChargeDtos.ChargeResponse(c.getId(), c.getCategory(), c.getDescription(),
                        c.getCalcType(), c.getValue(), c.getComputedAmount(), c.getSortOrder()))
                .toList();
        return new QuoteChargeDtos.Summary(money(materialsCost), money(fixedTotal),
                subtotalCost, money(percentTotal), operatingCost, profitCalcType, profitValue,
                profitAmount, taxableSubtotal, taxRate, tax, total, out);
    }

    private BigDecimal materialAmount(ProjectMaterial m) {
        BigDecimal unit = m.getUnitCostSnapshot() == null ? BigDecimal.ZERO : m.getUnitCostSnapshot();
        BigDecimal qty = m.getQuantityPlanned() == null ? BigDecimal.ZERO : m.getQuantityPlanned();
        return money(unit.multiply(qty));
    }

    private void requireQuote(Long quoteId, Long companyId) {
        quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
