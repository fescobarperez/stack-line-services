package com.erp_maya.platform.service;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/**
 * Los módulos que se venden y qué rutas de la API pertenece a cada uno. Vive
 * en código, no en la base: un módulo nuevo es código nuevo.
 *
 * Lo compartido entre módulos (ventas, pagos, proveedores, existencias)
 * queda en `base`: cotizaciones y FEL crean ventas, el POS cobra, etc.
 */
public final class ModuleCatalog {

    private ModuleCatalog() {}

    @Serdeable
    public record Module(String code, String name, String description, boolean alwaysOn) {}

    public static final String BASE = "base";

    public static final List<Module> MODULES = List.of(
            new Module(BASE, "Base", "Dashboard, clientes, productos, reportería, usuarios y configuración", true),
            new Module("pos", "Punto de venta", "POS, caja y cortes, devoluciones", false),
            new Module("fel", "Facturación FEL", "Facturación y documentos FEL", false),
            new Module("inventory_plus", "Inventario avanzado", "Variantes, conteo físico, transferencias, materia prima", false),
            new Module("purchases", "Compras", "Órdenes de compra y cuentas por pagar", false),
            new Module("quotes", "Cotizaciones y proyectos", "Cotizaciones, plan de cobro y proyectos", false),
            new Module("commercial", "Comercial", "Promociones, fidelización y cuentas por cobrar", false),
            new Module("accounting", "Contabilidad", "Contabilidad, bancos, presupuestos y activos fijos", false),
            new Module("payroll", "Planilla", "Empleados y planilla", false),
            new Module("ai", "Asistente de IA", "Tino en el ERP y en WhatsApp", false));

    public static final List<String> CODES = MODULES.stream().map(Module::code).toList();

    /** Prefijo de ruta → módulo. Lo que no aparece aquí es de `base`. */
    private static final Map<String, String> PREFIXES = Map.ofEntries(
            Map.entry("/api/cash-points", "pos"),
            Map.entry("/api/cash-registers", "pos"),
            Map.entry("/api/credit-notes", "pos"),
            Map.entry("/api/fel", "fel"),
            Map.entry("/api/product-variants", "inventory_plus"),
            Map.entry("/api/stock-counts", "inventory_plus"),
            Map.entry("/api/transfers", "inventory_plus"),
            Map.entry("/api/purchase-orders", "purchases"),
            Map.entry("/api/purchase-invoices", "purchases"),
            Map.entry("/api/supplier-payments", "purchases"),
            Map.entry("/api/quotes", "quotes"),
            Map.entry("/api/quote-conditions", "quotes"),
            Map.entry("/api/projects", "quotes"),
            Map.entry("/api/promotions", "commercial"),
            Map.entry("/api/loyalty", "commercial"),
            Map.entry("/api/receivables", "commercial"),
            Map.entry("/api/accounting", "accounting"),
            Map.entry("/api/accounting-periods", "accounting"),
            Map.entry("/api/accounts", "accounting"),
            Map.entry("/api/journal-entries", "accounting"),
            Map.entry("/api/bank-accounts", "accounting"),
            Map.entry("/api/budgets", "accounting"),
            Map.entry("/api/fixed-assets", "accounting"),
            Map.entry("/api/cost-centers", "accounting"),
            Map.entry("/api/employees", "payroll"),
            Map.entry("/api/payroll-periods", "payroll"),
            Map.entry("/api/agent", "ai"));

    /** Módulo de una ruta, o null si es de base (siempre permitida). */
    public static @Nullable String moduleFor(String path) {
        for (Map.Entry<String, String> e : PREFIXES.entrySet()) {
            String p = e.getKey();
            if (path.equals(p) || path.startsWith(p + "/") || path.startsWith(p + "?")) {
                return e.getValue();
            }
        }
        return null;
    }
}
