package com.erp_maya.payroll.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.payroll.domain.Employee;
import com.erp_maya.payroll.domain.PayrollEntry;
import com.erp_maya.payroll.domain.PayrollPeriod;
import com.erp_maya.payroll.dto.PayrollDtos;
import com.erp_maya.payroll.repository.EmployeeRepository;
import com.erp_maya.payroll.repository.PayrollPeriodRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Períodos de nómina y su procesamiento. Cálculo según legislación de Guatemala:
 * bonificación incentivo (Dcto. 78-89), IGSS (Dcto. 295) e ISR en relación de
 * dependencia (régimen general). Genera además los reportes de IGSS y SAT/ISR.
 */
@Singleton
public class PayrollService {

    // ── Constantes Guatemala ──────────────────────────────────────────────
    private static final BigDecimal IGSS_EMP  = new BigDecimal("0.0483"); // cuota laboral
    private static final BigDecimal IGSS_PAT  = new BigDecimal("0.1067"); // cuota patronal
    private static final BigDecimal BON_INC   = new BigDecimal("250");    // bonificación incentivo
    private static final BigDecimal EXENTO_ISR = new BigDecimal("48000"); // exento anual
    private static final BigDecimal DED_GMED   = new BigDecimal("12000"); // gastos médicos deducibles
    private static final BigDecimal TASA_ISR_1 = new BigDecimal("0.05");
    private static final BigDecimal TASA_ISR_2 = new BigDecimal("0.07");
    private static final BigDecimal UMBRAL_ISR = new BigDecimal("300000");
    private static final BigDecimal MESES      = new BigDecimal("12");

    private final PayrollPeriodRepository periods;
    private final EmployeeRepository employees;
    private final TenantContext tenant;

    public PayrollService(PayrollPeriodRepository periods, EmployeeRepository employees, TenantContext tenant) {
        this.periods = periods;
        this.employees = employees;
        this.tenant = tenant;
    }

    @Transactional
    public List<PayrollDtos.PeriodResponse> list() {
        return periods.findByCompanyIdOrderByYearDescMonthDesc(tenant.getCompanyId())
                .stream().map(PayrollService::toResponse).toList();
    }

    @Transactional
    public PayrollDtos.PeriodResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PayrollDtos.PeriodResponse create(PayrollDtos.PeriodRequest req) {
        return toResponse(findOrCreate(req));
    }

    /**
     * Genera la planilla en un solo paso: crea (o reutiliza) el período por su
     * código y lo procesa. Idempotente: reprocesar el mismo mes no da error de
     * llave duplicada; si estaba cerrado, se rechaza.
     */
    @Transactional
    public PayrollDtos.PeriodResponse generate(PayrollDtos.PeriodRequest req) {
        return toResponse(processEntries(findOrCreate(req)));
    }

    @Transactional
    public PayrollDtos.PeriodResponse process(Long id) {
        return toResponse(processEntries(find(id)));
    }

    @Transactional
    public PayrollDtos.PeriodResponse close(Long id) {
        PayrollPeriod period = find(id);
        period.setStatus("closed");
        return toResponse(periods.update(period));
    }

    /** Planilla de IGSS: cuota laboral (4.83%) y patronal (10.67%) por empleado. */
    @Transactional
    public PayrollDtos.IgssReport igssReport(Long id) {
        PayrollPeriod period = find(id);
        BigDecimal tBase = BigDecimal.ZERO, tLab = BigDecimal.ZERO, tPat = BigDecimal.ZERO;
        var lines = new ArrayList<PayrollDtos.IgssLine>();
        for (PayrollEntry e : period.getEntries()) {
            Employee emp = e.getEmployee();
            BigDecimal base = e.getBaseSalary() != null ? e.getBaseSalary() : BigDecimal.ZERO;
            BigDecimal lab = e.getIgssDeduction() != null ? e.getIgssDeduction() : base.multiply(IGSS_EMP).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pat = base.multiply(IGSS_PAT).setScale(2, RoundingMode.HALF_UP);
            tBase = tBase.add(base); tLab = tLab.add(lab); tPat = tPat.add(pat);
            lines.add(new PayrollDtos.IgssLine(
                    emp != null ? emp.getEmployeeCode() : null, emp != null ? emp.getName() : null,
                    emp != null ? emp.getDpi() : null, emp != null ? emp.getNit() : null,
                    base, lab, pat));
        }
        return new PayrollDtos.IgssReport(period.getId(), period.getName(), period.getEmployeeCount(),
                tBase, tLab, tPat, tLab.add(tPat), lines);
    }

    /** Formulario SAT — retención de ISR en relación de dependencia por empleado. */
    @Transactional
    public PayrollDtos.IsrReport isrReport(Long id) {
        PayrollPeriod period = find(id);
        BigDecimal tMonthly = BigDecimal.ZERO, tAnnual = BigDecimal.ZERO;
        var lines = new ArrayList<PayrollDtos.IsrLine>();
        for (PayrollEntry e : period.getEntries()) {
            Employee emp = e.getEmployee();
            BigDecimal base = e.getBaseSalary() != null ? e.getBaseSalary() : BigDecimal.ZERO;
            Calc c = calc(base);
            tMonthly = tMonthly.add(c.isrMensual); tAnnual = tAnnual.add(c.isrAnual);
            lines.add(new PayrollDtos.IsrLine(
                    emp != null ? emp.getEmployeeCode() : null, emp != null ? emp.getName() : null,
                    emp != null ? emp.getNit() : null,
                    base.multiply(MESES), c.rentaGravable, c.isrAnual, c.isrMensual));
        }
        return new PayrollDtos.IsrReport(period.getId(), period.getName(), period.getEmployeeCount(),
                tMonthly, tAnnual, lines);
    }

    // ── internos ──────────────────────────────────────────────────────────

    private PayrollPeriod findOrCreate(PayrollDtos.PeriodRequest req) {
        Long companyId = tenant.getCompanyId();
        return periods.findByCompanyIdAndPeriodCode(companyId, req.periodCode())
                .orElseGet(() -> {
                    PayrollPeriod p = new PayrollPeriod();
                    p.setCompanyId(companyId);
                    p.setPeriodCode(req.periodCode());
                    p.setName(req.name());
                    p.setMonth(req.month());
                    p.setYear(req.year());
                    p.setStatus("open");
                    return periods.save(p);
                });
    }

    private PayrollPeriod processEntries(PayrollPeriod period) {
        if ("closed".equals(period.getStatus())) {
            throw new IllegalStateException("El período ya está cerrado, no se puede reprocesar.");
        }
        period.getEntries().clear();

        BigDecimal total = BigDecimal.ZERO;
        List<Employee> active = employees.findByCompanyIdAndStatus(tenant.getCompanyId(), "active");
        for (Employee e : active) {
            BigDecimal base = e.getSalary() != null ? e.getSalary() : BigDecimal.ZERO;
            Calc c = calc(base);

            PayrollEntry entry = new PayrollEntry();
            entry.setEmployee(e);
            entry.setBaseSalary(base);
            entry.setBonuses(BON_INC);
            entry.setIgssDeduction(c.igss);
            entry.setIsrDeduction(c.isrMensual);
            entry.setOtherDeductions(BigDecimal.ZERO);
            entry.setNetPay(c.neto);
            period.addEntry(entry);
            total = total.add(c.neto);
        }
        period.setTotal(total);
        period.setEmployeeCount(active.size());
        period.setStatus("processed");
        return periods.update(period);
    }

    /** Cálculo de deducciones/líquido de un salario base mensual. */
    private static Calc calc(BigDecimal base) {
        BigDecimal igss = base.multiply(IGSS_EMP).setScale(2, RoundingMode.HALF_UP);
        BigDecimal salAnual = base.multiply(MESES);
        BigDecimal igssAnual = igss.multiply(MESES);
        BigDecimal renta = salAnual.subtract(EXENTO_ISR).subtract(igssAnual).subtract(DED_GMED);
        if (renta.signum() < 0) renta = BigDecimal.ZERO;
        BigDecimal isrAnual;
        if (renta.compareTo(UMBRAL_ISR) <= 0) {
            isrAnual = renta.multiply(TASA_ISR_1);
        } else {
            isrAnual = UMBRAL_ISR.multiply(TASA_ISR_1).add(renta.subtract(UMBRAL_ISR).multiply(TASA_ISR_2));
        }
        isrAnual = isrAnual.setScale(2, RoundingMode.HALF_UP);
        BigDecimal isrMensual = isrAnual.divide(MESES, 2, RoundingMode.HALF_UP);
        BigDecimal neto = base.add(BON_INC).subtract(igss).subtract(isrMensual);
        return new Calc(igss, isrMensual, isrAnual, renta, neto);
    }

    private record Calc(BigDecimal igss, BigDecimal isrMensual, BigDecimal isrAnual,
                        BigDecimal rentaGravable, BigDecimal neto) {}

    private PayrollPeriod find(Long id) {
        return periods.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Período de nómina " + id + " no encontrado"));
    }

    private static PayrollDtos.PeriodResponse toResponse(PayrollPeriod p) {
        var entries = p.getEntries().stream().map(e -> new PayrollDtos.EntryResponse(
                e.getId(), e.getEmployee() != null ? e.getEmployee().getId() : null,
                e.getEmployee() != null ? e.getEmployee().getName() : null,
                e.getBaseSalary(), e.getBonuses(), e.getIgssDeduction(), e.getIsrDeduction(),
                e.getOtherDeductions(), e.getNetPay())).toList();
        return new PayrollDtos.PeriodResponse(p.getId(), p.getPeriodCode(), p.getName(), p.getMonth(),
                p.getYear(), p.getStatus(), p.getTotal(), p.getEmployeeCount(), entries);
    }
}
