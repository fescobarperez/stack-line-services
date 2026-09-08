package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.quote.domain.QuotePaymentTerm;
import com.erp_maya.quote.dto.QuotePlanDtos;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.quote.repository.QuotePaymentTermRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.settings.service.TaxService;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.receivable.domain.Payment;
import com.erp_maya.receivable.repository.PaymentRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Plan de pagos de la cotización y su estado frente a los cobros reales
 * (payments imputados a la cotización).
 */
@Singleton
public class QuotePlanService {

    private final QuotePaymentTermRepository terms;
    private final PaymentRepository payments;
    private final QuoteRepository quotes;
    private final QuoteChargeService charges;
    private final TaxService taxService;
    private final TenantContext tenant;

    public QuotePlanService(QuotePaymentTermRepository terms, PaymentRepository payments,
                            QuoteRepository quotes, QuoteChargeService charges, TaxService taxService,
                            TenantContext tenant) {
        this.terms = terms;
        this.payments = payments;
        this.quotes = quotes;
        this.charges = charges;
        this.taxService = taxService;
        this.tenant = tenant;
    }

    @Transactional
    public QuotePlanDtos.Plan getPlan(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        return build(quoteId, companyId);
    }

    @Transactional
    public QuotePlanDtos.Plan addTerm(Long quoteId, QuotePlanDtos.TermRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote quote = requireQuote(quoteId, companyId);
        List<QuotePaymentTerm> existing = terms.findByCompanyIdAndQuoteIdOrderBySequenceAsc(companyId, quoteId);
        BigDecimal amount = money(req.amount());
        BigDecimal currentTotal = sumAmounts(existing);
        BigDecimal quoteTotal = quoteTotal(quote, companyId);
        BigDecimal nextTotal = money(currentTotal.add(amount));
        if (nextTotal.compareTo(quoteTotal) > 0) {
            throw new IllegalStateException("La suma del plan no puede exceder el total de la cotización (máximo disponible: "
                    + money(quoteTotal.subtract(currentTotal)) + ").");
        }
        int seq = req.sequence() != null ? req.sequence() : existing.size() + 1;
        QuotePaymentTerm term = new QuotePaymentTerm();
        term.setCompanyId(companyId);
        term.setQuoteId(quoteId);
        term.setSequence(seq);
        term.setAmount(amount);
        term.setDueDate(req.dueDate());
        term.setNotes(req.notes());
        terms.save(term);
        return build(quoteId, companyId);
    }

    /** El borrador puede estar incompleto; enviar al cliente exige igualdad exacta. */
    @Transactional
    public void validateReadyToSend(Long quoteId, Long companyId) {
        Quote quote = requireQuote(quoteId, companyId);
        BigDecimal quoteTotal = quoteTotal(quote, companyId);
        BigDecimal planTotal = sumAmounts(terms.findByCompanyIdAndQuoteIdOrderBySequenceAsc(companyId, quoteId));
        if (planTotal.compareTo(quoteTotal) != 0) {
            BigDecimal difference = quoteTotal.subtract(planTotal);
            String detail = difference.signum() > 0
                    ? "faltan " + money(difference)
                    : "excede el total por " + money(difference.abs());
            throw new IllegalStateException("No se puede enviar la cotización: el plan de pagos no cuadra; " + detail + ".");
        }
    }

    @Transactional
    public QuotePlanDtos.Plan deleteTerm(Long quoteId, Long termId) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        terms.findByIdAndCompanyId(termId, companyId)
                .filter(x -> x.getQuoteId().equals(quoteId))
                .ifPresent(terms::delete);
        return build(quoteId, companyId);
    }

    private QuotePlanDtos.Plan build(Long quoteId, Long companyId) {
        Quote quote = requireQuote(quoteId, companyId);
        List<QuotePaymentTerm> rows = terms.findByCompanyIdAndQuoteIdOrderBySequenceAsc(companyId, quoteId);
        BigDecimal quoteTotal = quoteTotal(quote, companyId);
        BigDecimal planTotal = sumAmounts(rows);
        BigDecimal remaining = money(quoteTotal.subtract(planTotal));

        List<Payment> paid = payments.findByCompanyIdAndQuoteIdOrderByPaymentDateDesc(companyId, quoteId);
        BigDecimal collected = paid.stream().map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pending = planTotal.subtract(collected).max(BigDecimal.ZERO);

        List<QuotePlanDtos.TermResponse> termOut = rows.stream()
                .map(t -> new QuotePlanDtos.TermResponse(t.getId(), t.getSequence(), money(t.getAmount()),
                        t.getDueDate(), t.getNotes()))
                .toList();
        List<QuotePlanDtos.PaymentRow> payOut = paid.stream()
                .map(p -> new QuotePlanDtos.PaymentRow(p.getId(), money(p.getAmount()), p.getPaymentDate(),
                        p.getMethod(), p.getReference(), p.getReceiptNumber()))
                .toList();
        return new QuotePlanDtos.Plan(quoteTotal, money(planTotal), remaining, money(collected), money(pending),
                remaining.signum() == 0, termOut, payOut);
    }

    private BigDecimal quoteTotal(Quote quote, Long companyId) {
        return money(charges.getSummary(quote.getId()).total());
    }

    private BigDecimal sumAmounts(List<QuotePaymentTerm> rows) {
        return money(rows.stream().map(QuotePaymentTerm::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private Quote requireQuote(Long quoteId, Long companyId) {
        return quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
