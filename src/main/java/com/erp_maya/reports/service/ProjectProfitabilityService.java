package com.erp_maya.reports.service;

import com.erp_maya.project.dto.ProjectDtos;
import com.erp_maya.project.service.ProjectService;
import com.erp_maya.reports.dto.ReportsDtos;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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

    private final ProjectService projects;

    public ProjectProfitabilityService(ProjectService projects) {
        this.projects = projects;
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
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
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

        return new ReportsDtos.ProjectProfitability(
                porPorcentaje ? "percent" : "amount", filtro, comparables.size(),
                totalMargen.setScale(2, RoundingMode.HALF_UP), pctPromedio, mejores, peores);
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
