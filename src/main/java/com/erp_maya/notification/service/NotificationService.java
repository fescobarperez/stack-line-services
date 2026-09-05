package com.erp_maya.notification.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.dashboard.repository.DashboardStockRepository;
import com.erp_maya.notification.dto.NotificationDtos.Notification;
import com.erp_maya.notification.repository.NotifPurchaseRepository;
import com.erp_maya.receivable.dto.AgingDtos.InvoiceRow;
import com.erp_maya.receivable.service.ReceivableAgingService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Notificaciones derivadas: alertas calculadas al momento (stock bajo, CxC vencida,
 * OCs pendientes). No hay tabla; el estado leído/no leído lo maneja el cliente.
 */
@Singleton
public class NotificationService {

    private final DashboardStockRepository stock;
    private final ReceivableAgingService aging;
    private final NotifPurchaseRepository purchases;
    private final TenantContext tenant;

    public NotificationService(DashboardStockRepository stock, ReceivableAgingService aging,
                               NotifPurchaseRepository purchases, TenantContext tenant) {
        this.stock = stock;
        this.aging = aging;
        this.purchases = purchases;
        this.tenant = tenant;
    }

    private static BigDecimal bd(Object v) {
        return v != null ? (BigDecimal) v : BigDecimal.ZERO;
    }

    private static String money(BigDecimal v) {
        return "Q " + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    @Transactional
    public List<Notification> list() {
        Long companyId = tenant.getCompanyId();
        Instant now = Instant.now();
        List<Notification> out = new ArrayList<>();

        // Stock bajo
        for (Object[] r : stock.lowStock(companyId)) {
            Long pid = (Long) r[0];
            String name = (String) r[2];
            out.add(new Notification("stock-" + pid, "stock_low", "Stock bajo: " + name,
                    "Existencia " + bd(r[3]).stripTrailingZeros().toPlainString()
                            + " / mínimo " + bd(r[4]).stripTrailingZeros().toPlainString(),
                    "inventory", now));
        }

        // CxC vencida
        for (InvoiceRow inv : aging.aging().invoices()) {
            if (inv.daysOverdue() > 0) {
                out.add(new Notification("cxc-" + inv.saleId(), "cxc", "Cobro vencido: " + inv.clientName(),
                        "Saldo " + money(inv.outstanding()) + " · " + inv.daysOverdue() + " días vencido",
                        "cxc", now));
            }
        }

        // Órdenes de compra pendientes
        for (Object[] r : purchases.pending(companyId)) {
            out.add(new Notification("po-" + r[0], "po_pending", "OC pendiente: " + r[1],
                    "Proveedor " + (r[2] != null ? r[2] : "—"), "purchases", now));
        }

        return out;
    }
}
