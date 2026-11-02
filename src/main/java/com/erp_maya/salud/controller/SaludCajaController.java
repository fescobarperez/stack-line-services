package com.erp_maya.salud.controller;

import com.erp_maya.salud.dto.CajaDtos;
import com.erp_maya.salud.service.FollowUpService;
import com.erp_maya.salud.service.SaludAccess;
import com.erp_maya.salud.service.SaludBillingService;
import com.erp_maya.salud.service.SaludReportService;
import com.erp_maya.salud.service.TreatmentPlanService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

/**
 * Caja y seguimiento de Stackline Salud (fase 7): paquetes, cuenta del
 * paciente, cobro de citas, planes de tratamiento, bandeja de controles y
 * reportes. Los permisos (`salud_billing`, `salud_followup`,
 * `salud_reports`, `clinical_record`) los validan los servicios.
 */
@Controller("/api/salud")
public class SaludCajaController {

    private final SaludBillingService billing;
    private final TreatmentPlanService plans;
    private final FollowUpService followUps;
    private final SaludReportService reports;

    public SaludCajaController(SaludBillingService billing, TreatmentPlanService plans, FollowUpService followUps,
                               SaludReportService reports) {
        this.billing = billing;
        this.plans = plans;
        this.followUps = followUps;
        this.reports = reports;
    }

    // ── Paquetes (catálogo) ──────────────────────────────────────────────

    @Get("/packages")
    public List<CajaDtos.PackageView> packages(Authentication auth) {
        return billing.listPackages(SaludAccess.of(auth));
    }

    @Post("/packages")
    @Status(HttpStatus.CREATED)
    public CajaDtos.PackageView createPackage(@Valid @Body CajaDtos.PackageRequest request, Authentication auth) {
        return billing.createPackage(SaludAccess.of(auth), request);
    }

    @Put("/packages/{id}")
    public CajaDtos.PackageView updatePackage(Long id, @Valid @Body CajaDtos.PackageRequest request, Authentication auth) {
        return billing.updatePackage(SaludAccess.of(auth), id, request);
    }

    // ── Cuenta del paciente ──────────────────────────────────────────────

    @Get("/patients/{id}/account")
    public CajaDtos.Account account(Long id, Authentication auth) {
        return billing.account(SaludAccess.of(auth), id);
    }

    /** Vende un paquete (lo factura en la caja abierta de quien vende). */
    @Post("/patients/{id}/packages")
    @Status(HttpStatus.CREATED)
    public CajaDtos.PatientPackageView sellPackage(Long id, @Valid @Body CajaDtos.SellPackageRequest request, Authentication auth) {
        return billing.sellPackage(SaludAccess.of(auth), id, request);
    }

    /** Recibe un anticipo (se factura al recibirlo). */
    @Post("/patients/{id}/deposits")
    @Status(HttpStatus.CREATED)
    public CajaDtos.CreditView deposit(Long id, @Valid @Body CajaDtos.DepositRequest request, Authentication auth) {
        return billing.deposit(SaludAccess.of(auth), id, request);
    }

    // ── Cobro de la cita ─────────────────────────────────────────────────

    @Get("/appointments/{id}/charge")
    public CajaDtos.ChargeOptions chargeOptions(Long id, Authentication auth) {
        return billing.options(SaludAccess.of(auth), id);
    }

    @Post("/appointments/{id}/charge")
    @Status(HttpStatus.CREATED)
    public CajaDtos.ChargeView charge(Long id, @Valid @Body CajaDtos.ChargeRequest request, Authentication auth) {
        return billing.charge(SaludAccess.of(auth), id, request);
    }

    @Post("/charges/{id}/void")
    public CajaDtos.VoidResult voidCharge(Long id, @Valid @Body CajaDtos.VoidRequest request, Authentication auth) {
        return billing.voidCharge(SaludAccess.of(auth), id, request);
    }

    // ── Planes de tratamiento ────────────────────────────────────────────

    @Get("/patients/{id}/plans")
    public List<CajaDtos.PlanView> plans(Long id, Authentication auth) {
        return plans.forPatient(SaludAccess.of(auth), id);
    }

    @Post("/cases/{id}/plans")
    @Status(HttpStatus.CREATED)
    public CajaDtos.PlanView createPlan(Long id, @Valid @Body CajaDtos.PlanRequest request, Authentication auth) {
        return plans.create(SaludAccess.of(auth), id, request);
    }

    @Post("/plans/{id}/cancel")
    public CajaDtos.PlanView cancelPlan(Long id, Authentication auth) {
        return plans.cancel(SaludAccess.of(auth), id);
    }

    // ── Controles ────────────────────────────────────────────────────────

    /** Bandeja: pendientes sin cita que vencen en los próximos `days` días (30) o ya vencieron. */
    @Get("/follow-ups")
    public List<CajaDtos.FollowUpItem> followUps(@Nullable @QueryValue Integer days,
                                                 @Nullable @QueryValue Long professionalId, Authentication auth) {
        return followUps.inbox(SaludAccess.of(auth), days, professionalId);
    }

    /** Cómo dice sentirse el paciente (lo manda TINO desde el recordatorio, o recepción). */
    @Post("/follow-ups/{id}/feedback")
    public CajaDtos.FeedbackResult feedback(Long id, @Valid @Body CajaDtos.FeedbackRequest request, Authentication auth) {
        return followUps.feedback(SaludAccess.of(auth), id, request);
    }

    // ── Reportes ─────────────────────────────────────────────────────────

    @Get("/reports")
    public CajaDtos.Report report(@QueryValue LocalDate from, @QueryValue LocalDate to, Authentication auth) {
        return reports.report(SaludAccess.of(auth), from, to);
    }
}
