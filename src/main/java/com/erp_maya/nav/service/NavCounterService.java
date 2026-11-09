package com.erp_maya.nav.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.dashboard.repository.DashboardStockRepository;
import com.erp_maya.nav.dto.NavCounters;
import com.erp_maya.nav.repository.NavCounterRepository;
import com.erp_maya.receivable.service.ReceivableAgingService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Contadores del menú lateral. Stock bajo y CxC vencidas reutilizan la lógica
 * del Dashboard y de Cuentas por Cobrar para que el número del menú coincida
 * con lo que muestra la pantalla.
 */
@Singleton
public class NavCounterService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final NavCounterRepository repo;
    private final DashboardStockRepository stock;
    private final ReceivableAgingService aging;
    private final TenantContext tenant;

    public NavCounterService(NavCounterRepository repo, DashboardStockRepository stock,
                             ReceivableAgingService aging, TenantContext tenant) {
        this.repo = repo;
        this.stock = stock;
        this.aging = aging;
        this.tenant = tenant;
    }

    @Transactional
    public NavCounters counters() {
        Long companyId = tenant.getCompanyId();
        LocalDate today = LocalDate.now(ZONE);
        long cxc = aging.aging().invoices().stream().filter(r -> r.daysOverdue() > 0).count();
        return new NavCounters(
                repo.ventasSinFel(companyId),
                repo.notasSinFel(companyId),
                stock.lowStock(companyId).size(),
                repo.ordenesAbiertas(companyId),
                repo.trasladosAbiertos(companyId),
                repo.cotizacionesParaAsesor(companyId),
                cxc,
                repo.facturasProveedorVencidas(companyId, today));
    }
}
