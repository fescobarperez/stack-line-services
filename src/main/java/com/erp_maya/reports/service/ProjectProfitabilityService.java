package com.erp_maya.reports.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.project.domain.ProjectCost;
import com.erp_maya.project.dto.ProjectDtos;
import com.erp_maya.project.repository.ProjectQuoteRepository;
import com.erp_maya.project.repository.ProjectRepositories.Costs;
import com.erp_maya.project.service.ProjectService;
import com.erp_maya.quote.repository.ChargeCategoryRepository;
import com.erp_maya.quote.repository.QuoteChargeRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.receivable.domain.Payment;
import com.erp_maya.receivable.repository.PaymentRepository;
import com.erp_maya.reports.dto.ReportsDtos;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Ranking de proyectos por rentabilidad: dónde se ganó más y dónde menos.
 *
 * Se apoya en ProjectService.list() y no en una consulta propia a propósito. El
 * margen de un proyecto no es una resta: descuenta material pendiente, cargos
 * de cotizaciones aprobadas y no aprobadas, y órdenes de compra vivas.
 * Reimplementarlo en SQL garantizaría que un día el ranking y la pantalla de
 * proyectos dijeran números distintos sobre el mismo proyecto, que es
 * exactamente el problema que se acaba de arreglar.
 */
@Singleton
public class ProjectProfitabilityService {

    /** Sin cerrar no hay resultado definitivo, así que es el default. */
    private static final String DEFAULT_STATUS = "closed";
    private static final int DEFAULT_LIMIT = 5;

    /** Estados de cotización firmes. Igual que en ProjectService. */
    private static final Set<String> APROBADAS = Set.of("aprobada", "convertida");

    private static final Map<String, String> ETIQUETA_ORIGEN = Map.of(
            "material", "Materiales consumidos",
            "labor", "Mano de obra",
            "purchase", "Compras",
            "other", "Otros");

    private final ProjectService projects;
    private final Costs costs;
    private final PaymentRepository payments;
    private final ProjectQuoteRepository projectQuotes;
    private final QuoteRepository quotes;
    private final QuoteChargeRepository quoteCharges;
    private final ChargeCategoryRepository chargeCategories;
    private final TenantContext tenant;

    public ProjectProfitabilityService(ProjectService projects, Costs costs, PaymentRepository payments,
                                       ProjectQuoteRepository projectQuotes, QuoteRepository quotes,
                                       QuoteChargeRepository quoteCharges,
                                       ChargeCategoryRepository chargeCategories, TenantContext tenant) {
        this.projects = projects;
        this.costs = costs;
        this.payments = payments;
        this.projectQuotes = projectQuotes;
        this.quotes = quotes;
        this.quoteCharges = quoteCharges;
        this.chargeCategories = chargeCategories;
        this.tenant = tenant;
    }

    /**
     * @param limit      cuántos en cada lista; 5 por defecto
     * @param orderBy    'amount' (quetzales) o 'percent' (margen %)
     * @param statusCsv  estados que entran, separados por coma; 'all' los toma todos
     */
    @Transactional
    public ReportsDtos.ProjectProfitability ranking(Integer limit, String orderBy, String statusCsv) {
        int k = limit != null && limit > 0 ? Math.min(limit, 50) : DEFAULT_LIMIT;
        boolean porPorcentaje = "percent".equalsIgnoreCase(orderBy);
        String filtro = statusCsv == null || statusCsv.isBlank() ? DEFAULT_STATUS : statusCsv.trim().toLowerCase();

        List<ProjectDtos.Response> candidatos = projects.list().stream()
                .filter(p -> aceptaEstado(p.status(), filtro))
                .toList();

        if (candidatos.isEmpty()) {
            return new ReportsDtos.ProjectProfitability(
                    porPorcentaje ? "percent" : "amount", filtro, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(),
                    List.of(), List.of(), List.of());
        }

        // Un proyecto sin monto contratado no tiene margen que comparar: el
        // porcentaje seria una division por cero y en quetzales aparenta una
        // perdida del tamanio de su costo. Se cuenta como evaluado pero no
        // compite.
        List<ProjectDtos.Response> comparables = candidatos.stream()
                .filter(p -> p.contracted() != null && p.contracted().signum() > 0)
                .toList();

        Comparator<ProjectDtos.Response> porMargen = porPorcentaje
                ? Comparator.comparing(p -> valor(p.marginPct()))
                : Comparator.comparing(p -> valor(p.margin()));

        List<ReportsDtos.ProjectMarginRow> mejores = comparables.stream()
                .sorted(porMargen.reversed())
                .limit(k).map(ProjectProfitabilityService::toRow).toList();
        List<ReportsDtos.ProjectMarginRow> peores = comparables.stream()
                .sorted(porMargen)
                .limit(k).map(ProjectProfitabilityService::toRow).toList();

        BigDecimal totalMargen = comparables.stream()
                .map(p -> valor(p.margin())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pctPromedio = comparables.isEmpty() ? BigDecimal.ZERO
                : comparables.stream().map(p -> valor(p.marginPct()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(comparables.size()), 2, RoundingMode.HALF_UP);

        List<Long> ids = candidatos.stream().map(ProjectDtos.Response::id).toList();
        return new ReportsDtos.ProjectProfitability(
                porPorcentaje ? "percent" : "amount", filtro, comparables.size(),
                totalMargen.setScale(2, RoundingMode.HALF_UP), pctPromedio, mejores, peores,
                composicion(ids), distribucion(comparables), flujo(ids));
    }

    /**
     * Composición del gasto agregada, con la MISMA regla que el ejecutado:
     * cargos del proyecto más cargos de cotizaciones aprobadas. Si aquí entrara
     * el material planificado, las porciones sumarían más que el margen que la
     * misma pantalla reporta.
     */
    private List<ReportsDtos.CompositionSlice> composicion(List<Long> projectIds) {
        Long companyId = tenant.getCompanyId();
        Map<String, BigDecimal> partes = new LinkedHashMap<>();
        Map<Long, String> nombreCategoria = new java.util.HashMap<>();
        chargeCategories.findByCompanyIdOrderBySortOrderAsc(companyId)
                .forEach(c -> nombreCategoria.put(c.getId(), c.getName()));

        for (Long projectId : projectIds) {
            for (ProjectCost c : costs.findByCompanyIdAndProjectId(companyId, projectId)) {
                acumula(partes, ETIQUETA_ORIGEN.getOrDefault(c.getSource(), "Otros"), c.getAmount());
            }
            for (var link : projectQuotes.findByCompanyIdAndProjectId(companyId, projectId)) {
                boolean aprobada = quotes.findByIdAndCompanyId(link.getQuoteId(), companyId)
                        .map(q -> q.getStatus() != null && APROBADAS.contains(q.getStatus().toLowerCase()))
                        .orElse(Boolean.FALSE);
                if (!aprobada) continue;
                quoteCharges.findByCompanyIdAndQuoteIdOrderBySortOrderAsc(companyId, link.getQuoteId())
                        .forEach(cargo -> acumula(partes,
                                cargo.getCategoryId() == null ? "Cargos sin clasificar"
                                        : nombreCategoria.getOrDefault(cargo.getCategoryId(), "Cargos sin clasificar"),
                                cargo.getComputedAmount()));
            }
        }
        return partes.entrySet().stream()
                .map(e -> new ReportsDtos.CompositionSlice(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(ReportsDtos.CompositionSlice::amount).reversed())
                .toList();
    }

    /**
     * Cuántos proyectos caen en cada tramo del coeficiente de variación.
     *
     * Los tramos son fijos y no cuantiles: con tres proyectos, unos cuantiles
     * repartirían uno en cada tercio y darían la impresión de una distribución
     * que no existe. Un tramo vacío dice más que uno inventado.
     */
    private List<ReportsDtos.MarginBucket> distribucion(List<ProjectDtos.Response> comparables) {
        String[] etiquetas = { "Pérdida (< 0)", "0 a 0.10", "0.10 a 0.25", "0.25 a 0.40", "Más de 0.40" };
        int[] conteo = new int[etiquetas.length];
        for (ProjectDtos.Response p : comparables) {
            double coef = valor(p.marginPct()).doubleValue() / 100d;
            int i = coef < 0 ? 0 : coef < 0.10 ? 1 : coef < 0.25 ? 2 : coef < 0.40 ? 3 : 4;
            conteo[i]++;
        }
        List<ReportsDtos.MarginBucket> out = new ArrayList<>();
        for (int i = 0; i < etiquetas.length; i++) out.add(new ReportsDtos.MarginBucket(etiquetas[i], conteo[i]));
        return out;
    }

    /**
     * Cobrado y gastado acumulados por fecha, sumando todos los proyectos.
     *
     * Se emite un punto por cada fecha con movimiento, no un punto por día: un
     * proyecto tiene pocos movimientos y muy espaciados, y rellenar el
     * calendario produciría cientos de puntos planos que no dicen nada.
     */
    private List<ReportsDtos.CashflowPoint> flujo(List<Long> projectIds) {
        Long companyId = tenant.getCompanyId();
        Map<LocalDate, BigDecimal[]> porFecha = new TreeMap<>();
        for (Long projectId : projectIds) {
            for (ProjectCost c : costs.findByCompanyIdAndProjectId(companyId, projectId)) {
                if (c.getCostDate() == null) continue;
                porFecha.computeIfAbsent(c.getCostDate(), k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO })[1]
                        = porFecha.get(c.getCostDate())[1].add(valor(c.getAmount()));
            }
            for (Payment pay : payments.findByCompanyIdAndProjectIdOrderByPaymentDateDesc(companyId, projectId)) {
                if (pay.getPaymentDate() == null) continue;
                porFecha.computeIfAbsent(pay.getPaymentDate(), k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO })[0]
                        = porFecha.get(pay.getPaymentDate())[0].add(valor(pay.getAmount()));
            }
        }
        List<ReportsDtos.CashflowPoint> out = new ArrayList<>();
        BigDecimal cobrado = BigDecimal.ZERO, gastado = BigDecimal.ZERO;
        for (var e : porFecha.entrySet()) {
            cobrado = cobrado.add(e.getValue()[0]);
            gastado = gastado.add(e.getValue()[1]);
            out.add(new ReportsDtos.CashflowPoint(e.getKey(),
                    cobrado.setScale(2, RoundingMode.HALF_UP), gastado.setScale(2, RoundingMode.HALF_UP)));
        }
        return out;
    }

    private void acumula(Map<String, BigDecimal> destino, String etiqueta, BigDecimal monto) {
        BigDecimal v = valor(monto);
        if (v.signum() <= 0) return;
        destino.merge(etiqueta, v, BigDecimal::add);
    }

    private boolean aceptaEstado(String status, String filtro) {
        if ("all".equals(filtro)) return true;
        Set<String> permitidos = Set.of(filtro.split("\\s*,\\s*"));
        return status != null && permitidos.contains(status.toLowerCase());
    }

    private static BigDecimal valor(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static ReportsDtos.ProjectMarginRow toRow(ProjectDtos.Response p) {
        return new ReportsDtos.ProjectMarginRow(
                p.id(), p.code(), p.name(), p.clientName(), p.status(),
                valor(p.contracted()), valor(p.executed()),
                valor(p.margin()), valor(p.marginPct()), valor(p.projectedMargin()),
                p.startDate(), p.endDate());
    }
}
