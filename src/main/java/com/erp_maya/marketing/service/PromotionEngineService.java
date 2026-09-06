package com.erp_maya.marketing.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.marketing.domain.Promotion;
import com.erp_maya.marketing.dto.PromotionEngineDtos.*;
import com.erp_maya.marketing.repository.PromotionRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Motor de promociones.
 *
 * Vivía en el navegador (src/data/promotions.js) sobre un catálogo quemado de
 * tres promociones de ejemplo, así que las que se configuraban en el módulo de
 * Promociones no se aplicaban nunca y el descuento lo calculaba el cliente.
 *
 * Aquí se evalúa contra las promociones reales y el resultado trae el descuento
 * YA REPARTIDO POR RENGLÓN: las promociones de carrito (monto fijo, mínimo de
 * compra, combo) se prorratean proporcionalmente entre las líneas elegibles,
 * que es lo que permite guardar `sale_items.discount` y `promotion_id`.
 */
@Singleton
public class PromotionEngineService {

    private final PromotionRepository promotions;
    private final TenantContext tenant;

    public PromotionEngineService(PromotionRepository promotions, TenantContext tenant) {
        this.promotions = promotions;
        this.tenant = tenant;
    }

    @Transactional
    public ApplyResponse apply(ApplyRequest req) {
        return apply(req, LocalDate.now(), LocalTime.now());
    }

    /**
     * Con fecha/hora explícitas para poder probarlo sin depender del reloj.
     * Lleva transacción propia: la llamada desde el otro `apply` es interna y
     * no pasa por el proxy, así que no heredaría la sesión.
     */
    @Transactional
    ApplyResponse apply(ApplyRequest req, LocalDate today, LocalTime now) {
        List<CartItem> items = req.items() == null ? List.of() : req.items();
        if (items.isEmpty()) return new ApplyResponse(List.of(), List.of(), BigDecimal.ZERO);

        BigDecimal subtotal = items.stream()
                .map(i -> i.unitPrice().multiply(i.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Descuento acumulado por renglón y quién lo aportó.
        Map<Long, BigDecimal> perLine = new LinkedHashMap<>();
        Map<Long, BigDecimal> topContributor = new HashMap<>();   // productId -> mayor aporte
        Map<Long, Long> linePromotion = new HashMap<>();          // productId -> promotionId
        List<AppliedPromotion> applied = new ArrayList<>();

        for (Promotion p : eligible(req, today, now)) {
            Map<Long, BigDecimal> share = switch (String.valueOf(p.getPromoType())) {
                case "pct_desc"   -> pctDesc(p, items);
                case "nxm"        -> nxm(p, items);
                case "precio_esp" -> specialPrice(p, items);
                case "monto_fijo" -> spread(p.getValue(), items, i -> true);
                case "min_compra" -> subtotal.compareTo(nz(p.getMinCompra())) >= 0
                        ? spread(p.getValue(), items, i -> true) : Map.of();
                case "combo"      -> combo(p, items);
                default           -> Map.of();
            };
            BigDecimal total = share.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() <= 0) continue;

            applied.add(new AppliedPromotion(p.getId(), p.getName(), label(p), total));
            share.forEach((productId, amount) -> {
                perLine.merge(productId, amount, BigDecimal::add);
                // sale_items guarda UNA promoción por renglón: se conserva la que
                // más aportó. El detalle completo queda en promotion_usage.
                if (amount.compareTo(topContributor.getOrDefault(productId, BigDecimal.ZERO)) > 0) {
                    topContributor.put(productId, amount);
                    linePromotion.put(productId, p.getId());
                }
            });
        }

        List<LineDiscount> lines = items.stream()
                .map(i -> new LineDiscount(i.productId(),
                        perLine.getOrDefault(i.productId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                        linePromotion.get(i.productId())))
                .toList();
        BigDecimal totalDiscount = lines.stream()
                .map(LineDiscount::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ApplyResponse(applied, lines, totalDiscount);
    }

    // ── Filtro de vigencia ───────────────────────────────────────────────
    private List<Promotion> eligible(ApplyRequest req, LocalDate today, LocalTime now) {
        String clientType = req.clientType() == null ? "Consumidor final" : req.clientType();
        return promotions.findByCompanyId(tenant.getCompanyId()).stream()
                .filter(p -> "active".equals(p.getStatus()))
                .filter(p -> p.getDateStart() == null || !today.isBefore(p.getDateStart()))
                .filter(p -> p.getDateEnd() == null || !today.isAfter(p.getDateEnd()))
                .filter(p -> matchesDay(p.getDays(), today.getDayOfWeek().getValue() % 7))
                .filter(p -> matchesHour(p, now))
                .filter(p -> p.getClientType() == null || "Todos".equals(p.getClientType())
                        || p.getClientType().equals(clientType))
                .toList();
    }

    /** `days` es "0,6" (0 = domingo), como lo guarda el módulo de Promociones. */
    private static boolean matchesDay(String days, int dow) {
        if (days == null || days.isBlank()) return true;
        for (String d : days.split(",")) {
            if (d.trim().equals(String.valueOf(dow))) return true;
        }
        return false;
    }

    private static boolean matchesHour(Promotion p, LocalTime now) {
        if (p.getHoraInicio() == null || p.getHoraInicio().isBlank()
                || p.getHoraFin() == null || p.getHoraFin().isBlank()) return true;
        try {
            return !now.isBefore(LocalTime.parse(p.getHoraInicio()))
                    && !now.isAfter(LocalTime.parse(p.getHoraFin()));
        } catch (Exception e) {
            return true;   // horario mal formado no debe bloquear la venta
        }
    }

    // ── Cálculo por tipo ─────────────────────────────────────────────────
    private static Map<Long, BigDecimal> pctDesc(Promotion p, List<CartItem> items) {
        BigDecimal pct = nz(p.getValue());
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (CartItem i : items) {
            if (p.getCategory() != null && !p.getCategory().isBlank()
                    && !p.getCategory().equalsIgnoreCase(i.category())) continue;
            BigDecimal base = i.unitPrice().multiply(i.quantity());
            out.put(i.productId(), base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return out;
    }

    /** Por cada N unidades, M salen gratis. */
    private static Map<Long, BigDecimal> nxm(Promotion p, List<CartItem> items) {
        if (p.getNxmN() == null || p.getNxmN() <= 0 || p.getNxmM() == null) return Map.of();
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (CartItem i : items) {
            boolean byName = p.getProduct() != null && p.getProduct().equalsIgnoreCase(i.name());
            boolean byCat  = (p.getProduct() == null || p.getProduct().isBlank())
                    && p.getCategory() != null && p.getCategory().equalsIgnoreCase(i.category());
            if (!byName && !byCat) continue;
            int qty = i.quantity().intValue();
            int free = (qty / p.getNxmN()) * p.getNxmM();
            if (free > 0) out.put(i.productId(), i.unitPrice().multiply(BigDecimal.valueOf(free)));
        }
        return out;
    }

    private static Map<Long, BigDecimal> specialPrice(Promotion p, List<CartItem> items) {
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (CartItem i : items) {
            if (p.getProduct() == null || !p.getProduct().equalsIgnoreCase(i.name())) continue;
            BigDecimal diff = i.unitPrice().subtract(nz(p.getValue()));
            if (diff.signum() > 0) out.put(i.productId(), diff.multiply(i.quantity()));
        }
        return out;
    }

    private static Map<Long, BigDecimal> combo(Promotion p, List<CartItem> items) {
        if (p.getProduct() == null || p.getProduct().isBlank()) return Map.of();
        List<String> names = Arrays.stream(p.getProduct().split(",")).map(String::trim).toList();
        boolean all = names.stream().allMatch(n ->
                items.stream().anyMatch(i -> n.equalsIgnoreCase(i.name())));
        if (!all) return Map.of();
        List<CartItem> inCombo = items.stream()
                .filter(i -> names.stream().anyMatch(n -> n.equalsIgnoreCase(i.name()))).toList();
        BigDecimal base = inCombo.stream()
                .map(i -> i.unitPrice().multiply(i.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = base.multiply(nz(p.getValue())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return spread(total, inCombo, i -> true);
    }

    /**
     * Reparte un descuento de carrito entre los renglones, proporcional a su
     * importe. Sin esto no se podría anotar en `sale_items` a qué línea
     * corresponde cada quetzal descontado.
     */
    private static Map<Long, BigDecimal> spread(BigDecimal amount, List<CartItem> items,
                                                java.util.function.Predicate<CartItem> filter) {
        BigDecimal total = nz(amount);
        if (total.signum() <= 0) return Map.of();
        List<CartItem> target = items.stream().filter(filter).toList();
        BigDecimal base = target.stream()
                .map(i -> i.unitPrice().multiply(i.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base.signum() <= 0) return Map.of();
        if (total.compareTo(base) > 0) total = base;   // nunca más que lo que vale

        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int k = 0; k < target.size(); k++) {
            CartItem i = target.get(k);
            BigDecimal share;
            if (k == target.size() - 1) {
                share = total.subtract(assigned);       // el último absorbe el redondeo
            } else {
                share = i.unitPrice().multiply(i.quantity())
                        .multiply(total).divide(base, 2, RoundingMode.HALF_UP);
                assigned = assigned.add(share);
            }
            out.put(i.productId(), share);
        }
        return out;
    }

    private static String label(Promotion p) {
        return switch (String.valueOf(p.getPromoType())) {
            case "pct_desc"   -> nz(p.getValue()).stripTrailingZeros().toPlainString() + "% desc"
                    + (p.getCategory() != null && !p.getCategory().isBlank() ? " en " + p.getCategory() : "");
            case "nxm"        -> p.getNxmN() + "×" + (p.getNxmN() - p.getNxmM()) + ": "
                    + (p.getProduct() != null ? p.getProduct() : p.getCategory());
            case "precio_esp" -> "Precio especial: " + p.getProduct();
            case "monto_fijo" -> "Descuento fijo Q" + nz(p.getValue()).stripTrailingZeros().toPlainString();
            case "min_compra" -> "Desc. mínimo de compra";
            case "combo"      -> "Combo: " + p.getProduct();
            default           -> p.getName();
        };
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
