package com.erp_maya.salud.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.salud.domain.AppointmentRow;
import com.erp_maya.salud.domain.Charge;
import com.erp_maya.salud.domain.PatientPackage;
import com.erp_maya.salud.domain.SaludCredit;
import com.erp_maya.salud.dto.CajaDtos;
import com.erp_maya.salud.repository.AppointmentRowRepository;
import com.erp_maya.salud.repository.AppointmentTypeRepository;
import com.erp_maya.salud.repository.ChargeRepository;
import com.erp_maya.salud.repository.PatientPackageRepository;
import com.erp_maya.salud.repository.SaludCreditRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.erp_maya.salud.service.SaludAccess.REPORTS;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Reportes básicos de la clínica para un periodo (fechas de Guatemala):
 * citas por día y por profesional, tasa de inasistencia, ingresos por
 * servicio y controles vencidos. Las cuentas viven en CajaRules; aquí solo
 * se junta lo del periodo.
 */
@Singleton
public class SaludReportService {

    static final int MAX_CITAS = 20000;
    static final int MAX_VENCIDOS = 100;

    private final AppointmentRowRepository rows;
    private final ChargeRepository charges;
    private final PatientPackageRepository patientPackages;
    private final SaludCreditRepository credits;
    private final AppointmentTypeRepository types;
    private final FollowUpService followUps;
    private final TenantContext tenant;

    public SaludReportService(AppointmentRowRepository rows, ChargeRepository charges,
                              PatientPackageRepository patientPackages, SaludCreditRepository credits,
                              AppointmentTypeRepository types, FollowUpService followUps, TenantContext tenant) {
        this.rows = rows;
        this.charges = charges;
        this.patientPackages = patientPackages;
        this.credits = credits;
        this.types = types;
        this.followUps = followUps;
        this.tenant = tenant;
    }

    @Transactional
    public CajaDtos.Report report(SaludAccess.Actor actor, LocalDate from, LocalDate to) {
        actor.require(REPORTS, VER);
        CajaRules.checkPeriodo(from, to);
        Long companyId = tenant.getCompanyId();
        Instant desde = from.atStartOfDay(AgendaRules.GUATEMALA).toInstant();
        Instant hasta = to.plusDays(1).atStartOfDay(AgendaRules.GUATEMALA).toInstant();

        List<CajaRules.CitaHecho> citas = rows.search(companyId, desde, hasta, 0L, 0L, 0L, "", MAX_CITAS).stream()
                .filter(r -> !r.getStartsAt().isBefore(desde))
                .map(SaludReportService::hecho).toList();

        Map<Long, String> tipos = new HashMap<>();
        types.findByCompanyIdOrderByName(companyId).forEach(t -> tipos.put(t.getId(), t.getName()));
        List<CajaRules.CobroHecho> cobros = charges
                .findByCompanyIdAndStatusAndCreatedAtGreaterThanEqualsAndCreatedAtLessThan(companyId, CajaRules.VIGENTE, desde, hasta)
                .stream().map(c -> hecho(c, tipos)).toList();
        BigDecimal paquetes = patientPackages
                .findByCompanyIdAndPurchasedAtGreaterThanEqualsAndPurchasedAtLessThan(companyId, desde, hasta).stream()
                .filter(p -> p.getSaleId() != null && !CajaRules.PK_ANULADO.equals(p.getStatus()))
                .map(PatientPackage::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal anticipos = credits
                .findByCompanyIdAndKindAndCreatedAtGreaterThanEqualsAndCreatedAtLessThan(companyId, CajaRules.CR_ANTICIPO, desde, hasta)
                .stream().map(SaludCredit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CajaDtos.Report(from, to, CajaRules.totales(citas, cobros, paquetes, anticipos),
                CajaRules.porDia(citas), CajaRules.porProfesional(citas), CajaRules.porServicio(cobros),
                followUps.overdue(actor, MAX_VENCIDOS));
    }

    static CajaRules.CitaHecho hecho(AppointmentRow r) {
        return new CajaRules.CitaHecho(r.getStartsAt().atZone(AgendaRules.GUATEMALA).toLocalDate(), r.getProfessionalId(),
                r.getProfessionalName(), r.getStatus());
    }

    static CajaRules.CobroHecho hecho(Charge c, Map<Long, String> tipos) {
        return new CajaRules.CobroHecho(c.getTypeId(), tipos.getOrDefault(c.getTypeId(), "Tipo " + c.getTypeId()),
                c.getMethod(), c.getServiceValue(), c.getCashAmount(), c.getCreditAmount());
    }
}
