package com.erp_maya.quote.controller;

import com.erp_maya.quote.dto.QuoteDtos;
import com.erp_maya.quote.dto.QuoteChargeDtos;

import java.util.List;
import com.erp_maya.quote.dto.QuotePlanDtos;
import com.erp_maya.quote.service.QuoteService;
import com.erp_maya.mail.dto.MailSettingsDtos;
import com.erp_maya.mail.service.MailService;
import com.erp_maya.mail.service.QuoteMailService;
import com.erp_maya.quote.service.QuoteChargeService;
import com.erp_maya.quote.service.QuotePlanService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/quotes")
public class QuoteController {

    private final QuoteService service;
    private final QuoteChargeService charges;
    private final QuotePlanService plan;
    private final QuoteMailService quoteMail;

    public QuoteController(QuoteService service, QuoteChargeService charges, QuotePlanService plan,
                           QuoteMailService quoteMail) {
        this.quoteMail = quoteMail;
        this.service = service;
        this.charges = charges;
        this.plan = plan;
    }

    @Get
    public Page<QuoteDtos.Response> list(@Nullable @QueryValue String partyType, Pageable pageable) {
        return service.list(partyType, pageable);
    }

    @Get("/{id}")
    public QuoteDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public QuoteDtos.Response create(@Valid @Body QuoteDtos.Request request) {
        return service.create(request);
    }

    @Put("/{id}")
    public QuoteDtos.Response update(Long id, @Valid @Body QuoteDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    /**
     * Al pasar a «enviada» se manda la cotización al cliente.
     *
     * El correo va DESPUÉS de que el cambio de estado esté guardado, y aquí y
     * no dentro del servicio para no sostener la transacción durante los
     * segundos que puede tardar un SMTP. Si el envío falla, el estado ya
     * cambió: el fallo queda en la bitácora de la cotización, no deshace nada.
     */
    @Put("/{id}/status")
    public QuoteDtos.Response updateStatus(Long id, @Valid @Body QuoteDtos.StatusRequest request) {
        QuoteDtos.Response saved = service.updateStatus(id, request);
        if ("enviada".equalsIgnoreCase(saved.status())) {
            quoteMail.enviar(id);
        }
        return saved;
    }

    /** Reenvío manual, para cuando el cliente dice que no le llegó. */
    @Post("/{id}/send-email")
    public MailSettingsDtos.TestResult sendEmail(Long id) {
        MailService.Resultado r = quoteMail.enviar(id);
        return new MailSettingsDtos.TestResult(r.ok(), r.mensaje());
    }

    // ── Gastos / cargos de la cotización ──────────────────────────────────
    @Get("/{id}/charges")
    public QuoteChargeDtos.Summary charges(Long id) {
        return charges.getSummary(id);
    }

    @Post("/{id}/charges")
    @Status(HttpStatus.CREATED)
    public QuoteChargeDtos.Summary addCharge(Long id, @Valid @Body QuoteChargeDtos.Request request) {
        return charges.addCharge(id, request);
    }

    @Delete("/{id}/charges/{chargeId}")
    public QuoteChargeDtos.Summary deleteCharge(Long id, Long chargeId) {
        return charges.deleteCharge(id, chargeId);
    }

    // ── Plan de pagos y cobros de la cotización ───────────────────────────
    /**
     * Catálogo de categorías de gasto. Segmento literal: "charge-categories"
     * no convierte a Long, así que no compite con @Get("/{id}").
     */
    @Get("/charge-categories")
    public List<QuoteChargeDtos.CategoryResponse> chargeCategories() {
        return charges.listCategories();
    }

    /** Ajuste de cierre sobre la base antes de IVA. Negativo = descuento. */
    @Put("/{id}/adjustment")
    public QuoteChargeDtos.Summary setAdjustment(
            Long id, @Valid @Body QuoteChargeDtos.AdjustmentRequest request) {
        return charges.setAdjustment(id, request.amount());
    }

    /** Cambia entre monto único y desglose para el gasto operativo. */
    @Put("/{id}/operating-mode")
    public QuoteChargeDtos.Summary setOperatingMode(
            Long id, @Valid @Body QuoteChargeDtos.OperatingModeRequest request) {
        return charges.setOperatingMode(id, request.mode());
    }

    /** Porcentaje de gasto operativo sobre el subtotal; cambia al modo 'percent'. */
    @Put("/{id}/operating-pct")
    public QuoteChargeDtos.Summary setOperatingPct(
            Long id, @Valid @Body QuoteChargeDtos.OperatingPctRequest request) {
        return charges.setOperatingPct(id, request.pct());
    }

    /** Fija el gasto operativo como una sola cifra; cero lo elimina. */
    @Put("/{id}/operating-expense")
    public QuoteChargeDtos.Summary setOperatingAmount(
            Long id, @Valid @Body QuoteChargeDtos.OperatingAmountRequest request) {
        return charges.setOperatingAmount(id, request);
    }

    @Post("/charge-categories")
    @Status(HttpStatus.CREATED)
    public QuoteChargeDtos.CategoryResponse createChargeCategory(
            @Valid @Body QuoteChargeDtos.CategoryRequest request) {
        return charges.createCategory(request);
    }

    @Put("/charge-categories/{id}")
    public QuoteChargeDtos.CategoryResponse updateChargeCategory(
            Long id, @Valid @Body QuoteChargeDtos.CategoryRequest request) {
        return charges.updateCategory(id, request);
    }

    @Delete("/charge-categories/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void deleteChargeCategory(Long id) {
        charges.deleteCategory(id);
    }

    @Get("/{id}/plan")
    public QuotePlanDtos.Plan plan(Long id) {
        return plan.getPlan(id);
    }

    @Post("/{id}/plan/terms")
    @Status(HttpStatus.CREATED)
    public QuotePlanDtos.Plan addTerm(Long id, @Valid @Body QuotePlanDtos.TermRequest request) {
        return plan.addTerm(id, request);
    }

    /**
     * Reparte el total en N cuotas y reemplaza el plan. Bloquea si ya hay
     * cobros: borrar las cuotas contra las que alguien pagó dejaría los pagos
     * sin el plan que los justifica.
     */
    @Post("/{id}/plan/generate")
    public QuotePlanDtos.Plan generatePlan(Long id, @Valid @Body QuotePlanDtos.GenerateRequest request) {
        return plan.generate(id, request);
    }

    @Delete("/{id}/plan/terms/{termId}")
    public QuotePlanDtos.Plan deleteTerm(Long id, Long termId) {
        return plan.deleteTerm(id, termId);
    }
}
