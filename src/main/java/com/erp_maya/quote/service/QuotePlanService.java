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
import java.time.LocalDate;
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

    /**
     * Rehace el plan repartiendo el total en N cuotas.
     *
     * Reemplaza lo que hubiera: un plan mitad manual y mitad generado no lo
     * sabe explicar nadie. Por eso se bloquea si ya hay cobros imputados —
     * borrar las cuotas contra las que alguien ya pagó dejaría los pagos
     * huérfanos del plan que los justificaba.
     *
     * El residuo del redondeo va a la PRIMERA cuota, no a la última: es la
     * práctica del medio y deja las siguientes en cifras parejas, que es lo
     * que el cliente ve en el papel.
     *
     *   Q 1,000.00 en 3  →  Q 333.34 + Q 333.33 + Q 333.33
     */
    @Transactional
    public QuotePlanDtos.Plan generate(Long quoteId, QuotePlanDtos.GenerateRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote quote = requireQuote(quoteId, companyId);

        BigDecimal cobrado = payments.findByCompanyIdAndQuoteIdOrderByPaymentDateDesc(companyId, quoteId)
                .stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (cobrado.signum() > 0) {
            throw new IllegalStateException("La cotización ya tiene cobros por " + money(cobrado)
                    + "; el plan no se puede regenerar. Elimina los cobros o ajusta las cuotas a mano.");
        }

        BigDecimal total = quoteTotal(quote, companyId);
        if (total.signum() <= 0) {
            throw new IllegalStateException("La cotización no tiene total que repartir");
        }

        BigDecimal anticipo = calcularAnticipo(req, total);
        BigDecimal resto = money(total.subtract(anticipo));
        if (resto.signum() <= 0) {
            throw new IllegalStateException("El anticipo no puede cubrir el total: no quedarían cuotas que generar");
        }

        int cuotas = req.installments();
        // DOWN y no HALF_UP: así el residuo nunca es negativo y siempre sobra
        // algo para cargarle a la primera, en vez de quedar debiendo centavos.
        BigDecimal base = resto.divide(BigDecimal.valueOf(cuotas), 2, RoundingMode.DOWN);
        BigDecimal residuo = money(resto.subtract(base.multiply(BigDecimal.valueOf(cuotas))));

        terms.deleteAll(terms.findByCompanyIdAndQuoteIdOrderBySequenceAsc(companyId, quoteId));

        int secuencia = 1;
        LocalDate inicio = req.startDate();
        if (anticipo.signum() > 0) {
            guardarCuota(companyId, quoteId, secuencia++, anticipo, inicio, "Anticipo");
        }
        for (int i = 0; i < cuotas; i++) {
            BigDecimal monto = i == 0 ? money(base.add(residuo)) : base;
            // Con anticipo la primera cuota cae un período después: el anticipo
            // ya ocupó la fecha inicial.
            int periodos = anticipo.signum() > 0 ? i + 1 : i;
            LocalDate vence = inicio == null ? null : desplazar(inicio, periodos, req);
            guardarCuota(companyId, quoteId, secuencia++, monto, vence,
                    "Cuota " + (i + 1) + " de " + cuotas);
        }
        return build(quoteId, companyId);
    }

    private BigDecimal calcularAnticipo(QuotePlanDtos.GenerateRequest req, BigDecimal total) {
        BigDecimal valor = req.advanceValue();
        if (valor == null || valor.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal anticipo = "percent".equalsIgnoreCase(req.advanceCalcType())
                ? money(total.multiply(valor).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP))
                : money(valor);
        if (anticipo.compareTo(total) >= 0) {
            throw new IllegalStateException("El anticipo (" + money(anticipo)
                    + ") no puede ser igual o mayor al total de la cotización (" + money(total) + ")");
        }
        return anticipo;
    }

    /** Fecha de la cuota número `periodos` contando desde la inicial. */
    private LocalDate desplazar(LocalDate inicio, int periodos, QuotePlanDtos.GenerateRequest req) {
        String frecuencia = req.frequency() == null ? "mensual" : req.frequency().trim().toLowerCase();
        return switch (frecuencia) {
            case "semanal" -> inicio.plusWeeks(periodos);
            case "quincenal" -> inicio.plusDays(15L * periodos);
            case "dias" -> {
                Integer cada = req.everyDays();
                if (cada == null || cada < 1) {
                    throw new IllegalStateException("Indica cada cuántos días vence cada cuota");
                }
                yield inicio.plusDays((long) cada * periodos);
            }
            case "mensual" -> inicio.plusMonths(periodos);
            default -> throw new IllegalStateException("Frecuencia no válida: " + req.frequency());
        };
    }

    private void guardarCuota(Long companyId, Long quoteId, int secuencia,
                              BigDecimal monto, LocalDate vence, String notas) {
        QuotePaymentTerm cuota = new QuotePaymentTerm();
        cuota.setCompanyId(companyId);
        cuota.setQuoteId(quoteId);
        cuota.setSequence(secuencia);
        cuota.setAmount(monto);
        cuota.setDueDate(vence);
        cuota.setNotes(notas);
        terms.save(cuota);
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
