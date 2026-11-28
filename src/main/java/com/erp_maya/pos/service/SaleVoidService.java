package com.erp_maya.pos.service;

import com.erp_maya.accounting.domain.JournalEntry;
import com.erp_maya.accounting.repository.JournalEntryRepository;
import com.erp_maya.accounting.service.PostingCase;
import com.erp_maya.accounting.service.PostingService;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.fel.service.FelService;
import com.erp_maya.inventory.service.StockService;
import com.erp_maya.marketing.repository.PromotionUsageRepository;
import com.erp_maya.pos.domain.CashRegister;
import com.erp_maya.pos.domain.Sale;
import com.erp_maya.pos.domain.SaleItem;
import com.erp_maya.pos.dto.SaleDtos;
import com.erp_maya.pos.repository.CashRegisterRepository;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.receivable.repository.PaymentRepository;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import com.erp_maya.settings.repository.CompanySettingRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Anula una venta de punta a punta. Deshace todo lo que hizo al registrarse:
 *
 *   - DTE: lo anula ante la SAT (simulado, como la certificación).
 *   - Contabilidad: la partida de la venta se conserva (es un hecho) y se
 *     registra su contrapartida con la plantilla asociada a la anulación
 *     (pos.anulacion.contado / .credito, en Contabilidad → Partidas
 *     automáticas). Sin plantilla asociada, la anulación se bloquea.
 *   - Inventario: devuelve a bodega lo vendido, con su movimiento en el kardex.
 *   - Caja: si el turno sigue abierto, saca la venta de sus totales.
 *   - Promociones: sus usos dejan de contar.
 *
 * Y queda en estado 'cancelled', el que ya excluyen el saldo de clientes, la
 * antigüedad de CxC, lo facturado de proyectos, el tablero y los reportes.
 *
 * Se niega a anular lo que no se puede deshacer limpio: una venta con abonos
 * (el dinero ya entró contra ella), una con notas de crédito vigentes, o una
 * fuera del plazo que la empresa configure (fel.annul_max_hours).
 */
@Singleton
public class SaleVoidService {

    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final PromotionUsageRepository promotionUsage;
    private final CashRegisterRepository registers;
    private final UserRepository users;
    private final StockService stock;
    private final JournalEntryRepository entries;
    private final PostingService posting;
    private final FelService fel;
    private final CompanySettingRepository settings;
    private final TenantContext tenant;

    public SaleVoidService(SaleRepository sales, PaymentRepository payments, PromotionUsageRepository promotionUsage,
                           CashRegisterRepository registers, UserRepository users, StockService stock,
                           JournalEntryRepository entries, PostingService posting, FelService fel,
                           CompanySettingRepository settings, TenantContext tenant) {
        this.sales = sales;
        this.payments = payments;
        this.promotionUsage = promotionUsage;
        this.registers = registers;
        this.users = users;
        this.stock = stock;
        this.entries = entries;
        this.posting = posting;
        this.fel = fel;
        this.settings = settings;
        this.tenant = tenant;
    }

    @Transactional
    public SaleDtos.Response anular(Long saleId, String motivo, Long userId) {
        Long companyId = tenant.getCompanyId();
        Sale s = sales.findByIdAndCompanyId(saleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Venta " + saleId + " no encontrada"));
        String doc = s.getDocNumber() != null ? s.getDocNumber() : "#" + s.getId();
        String razon = motivo == null ? "" : motivo.trim();

        if ("cancelled".equalsIgnoreCase(s.getStatus())) {
            throw new IllegalStateException("La venta " + doc + " ya está anulada.");
        }
        if (razon.length() < 5) {
            throw new IllegalStateException("Escribe el motivo de la anulación (al menos 5 caracteres).");
        }
        Integer plazo = plazoHoras(companyId);
        if (plazo != null && s.getSaleDate() != null
                && Instant.now().isAfter(s.getSaleDate().plus(plazo, ChronoUnit.HOURS))) {
            throw new IllegalStateException("La venta " + doc + " tiene más de " + plazo
                    + " horas: venció el plazo de anulación configurado (Configuración → FEL).");
        }
        if (payments.existsBySaleIdAndCompanyId(s.getId(), companyId)) {
            throw new IllegalStateException("La venta " + doc + " tiene cobros registrados; "
                    + "no se puede anular mientras tenga abonos aplicados.");
        }
        if (sales.existsByCompanyIdAndRelatedSaleIdAndStatusNot(companyId, s.getId(), "cancelled")) {
            throw new IllegalStateException("La venta " + doc + " tiene notas de crédito vigentes; anúlalas primero.");
        }

        User user = userId == null ? null : users.findByIdAndCompanyId(userId, companyId).orElse(null);

        // Inventario: el movimiento inverso del que hizo la venta (que restó la cantidad).
        for (SaleItem item : s.getItems()) {
            if (item.getProduct() == null) continue;   // renglón de servicio
            stock.applyMovement(item.getProduct(), s.getBranch(), user, "sale_void",
                    item.getQuantity(), "sale", doc, "");
        }

        // Caja: solo si el turno sigue abierto. Uno cerrado ya se arqueó y sus
        // totales son historia; el dinero que se devuelva hoy sale del turno de hoy.
        CashRegister r = s.getCashRegister();
        if (r != null && !s.isCredit() && "open".equals(r.getStatus())) {
            BigDecimal total = s.getTotal() != null ? s.getTotal() : BigDecimal.ZERO;
            r.setSalesTotal(r.getSalesTotal().subtract(total));
            String metodo = s.getPaymentMethod() != null ? s.getPaymentMethod().toLowerCase() : "";
            if ("efectivo".equals(metodo)) {
                r.setSalesCash(r.getSalesCash().subtract(total));
            } else if (metodo.contains("tarjeta")) {
                r.setSalesCard(r.getSalesCard().subtract(total));
            }
            registers.update(r);
        }

        // Contabilidad: la partida de la venta queda como está y la anulación
        // registra su contrapartida, con los mismos montos (con signo, como la
        // venta: una NCRE los trae negativos) y el mismo centro de costo.
        JournalEntry original = entries.findByCompanyIdAndSourceTypeAndSourceId(companyId, "sale", s.getId()).stream()
                .filter(e -> e.getReversesEntryId() == null && !"void".equalsIgnoreCase(e.getStatus()))
                .findFirst().orElse(null);
        if (original != null) {
            BigDecimal total = s.getSignedTotal() != null ? s.getSignedTotal()
                    : (s.getTotal() != null ? s.getTotal() : BigDecimal.ZERO);
            BigDecimal iva = s.getTax() != null ? s.getTax() : BigDecimal.ZERO;
            if (total.signum() < 0) iva = iva.negate();
            Long centro = original.getLines().stream()
                    .map(l -> l.getCostCenterId()).filter(c -> c != null).findFirst().orElse(null);
            String desc = "Anulación " + doc + ": " + (razon.length() > 120 ? razon.substring(0, 120) : razon);
            posting.postCase(s.isCredit() ? PostingCase.POS_ANULACION_CREDITO : PostingCase.POS_ANULACION_CONTADO,
                    "sale_void", s.getId(), LocalDate.now(), desc, doc,
                    s.getClient() != null ? s.getClient().getName() : null,
                    Map.of("total", total, "base", total.subtract(iva), "iva", iva), null, centro);
        }

        promotionUsage.deleteByCompanyIdAndSaleId(companyId, s.getId());
        fel.anularDeVenta(s, razon);

        s.setStatus("cancelled");
        s.setVoidedAt(Instant.now());
        s.setVoidReason(razon);
        s.setVoidedBy(user != null ? user.getId() : null);
        return SaleService.toResponse(sales.update(s));
    }

    /** Plazo de anulación en horas (fel.annul_max_hours); sin configurar, no hay plazo. */
    private Integer plazoHoras(Long companyId) {
        return settings.findByCompanyIdAndSettingKey(companyId, "fel.annul_max_hours")
                .map(x -> x.getSettingValue())
                .filter(v -> v != null && v.trim().matches("\\d+"))
                .map(v -> Integer.valueOf(v.trim()))
                .filter(h -> h > 0)
                .orElse(null);
    }
}
