package com.erp_maya.accounting.service;

import java.util.List;

/**
 * El catálogo mínimo de cuentas y las plantillas con que nace cada empresa.
 *
 * Son las mismas definiciones que siembran las migraciones 095 y 097 para las
 * empresas que ya existían: si cambia una, cambia la otra. Equivalen a lo que antes
 * estaba escrito en Java en cada módulo, así que una empresa nueva contabiliza
 * igual que las de siempre hasta que alguien cambie sus plantillas.
 */
public final class DefaultPostingTemplates {

    private DefaultPostingTemplates() {}

    /** Cuenta del catálogo mínimo y la clave posting.* que la apunta. */
    public record DefaultAccount(String code, String name, String normalBalance, String postingKey) {}

    /** Línea por defecto: la cuenta va por código del catálogo mínimo. */
    public record DefaultLine(String side, String accountCode, boolean usesDocumentAccount,
                              List<String> amounts, String description, boolean useCostCenter) {}

    public record DefaultTemplate(PostingCase useCase, String name, String description, List<DefaultLine> lines) {}

    /** Cuenta de agrupación (no admite partidas) de los niveles 1 a 3 del catálogo mínimo. */
    public record DefaultGroup(String code, String name, String normalBalance, int level) {}

    /**
     * Los niveles de arriba de las cuentas del catálogo mínimo, para que el
     * árbol esté completo (AccountTreeRules): 1 Activo → 11 Activo corriente →
     * 1101 Efectivo → 110101 Caja. Mismas definiciones que la migración 098.
     */
    public static final List<DefaultGroup> GROUPS = List.of(
            new DefaultGroup("1", "Activo", "debit", 1),
            new DefaultGroup("11", "Activo corriente", "debit", 2),
            new DefaultGroup("1101", "Efectivo y equivalentes", "debit", 3),
            new DefaultGroup("1102", "Cuentas por cobrar", "debit", 3),
            new DefaultGroup("1103", "Inventarios", "debit", 3),
            new DefaultGroup("1104", "Impuestos por cobrar", "debit", 3),
            new DefaultGroup("2", "Pasivo", "credit", 1),
            new DefaultGroup("21", "Pasivo corriente", "credit", 2),
            new DefaultGroup("2101", "Cuentas por pagar", "credit", 3),
            new DefaultGroup("2102", "Impuestos por pagar", "credit", 3),
            new DefaultGroup("2103", "Anticipos recibidos", "credit", 3),
            new DefaultGroup("4", "Ingresos", "credit", 1),
            new DefaultGroup("41", "Ingresos de operación", "credit", 2),
            new DefaultGroup("4101", "Ventas", "credit", 3),
            new DefaultGroup("5", "Costos", "debit", 1),
            new DefaultGroup("51", "Costo de ventas", "debit", 2),
            new DefaultGroup("5101", "Costo de ventas", "debit", 3));

    /** Nivel de las cuentas de detalle del catálogo mínimo. */
    public static final int DETAIL_LEVEL = 4;

    /**
     * El código de la cuenta padre según la nomenclatura del catálogo mínimo:
     * 6 dígitos → 4, 4 → 2, 2 → 1. Null para una de nivel 1.
     */
    public static String parentCode(String code) {
        return switch (code.length()) {
            case 6 -> code.substring(0, 4);
            case 4 -> code.substring(0, 2);
            case 2 -> code.substring(0, 1);
            default -> null;
        };
    }

    public static final List<DefaultAccount> ACCOUNTS = List.of(
            new DefaultAccount("110101", "Caja", "debit", "posting.cash"),
            new DefaultAccount("110102", "Bancos", "debit", "posting.bank"),
            new DefaultAccount("110201", "Clientes", "debit", "posting.receivable"),
            new DefaultAccount("110301", "Inventario de mercaderia", "debit", "posting.inventory"),
            new DefaultAccount("110401", "IVA credito fiscal", "debit", "posting.tax_credit"),
            new DefaultAccount("410101", "Ingresos por ventas", "credit", "posting.revenue"),
            new DefaultAccount("210101", "Proveedores", "credit", "posting.payable"),
            new DefaultAccount("210201", "IVA debito fiscal", "credit", "posting.tax_payable"),
            new DefaultAccount("210301", "Anticipos de clientes", "credit", "posting.customer_advance"),
            new DefaultAccount("510101", "Costo de ventas", "debit", "posting.cost_of_sales"));

    private static final String D = TemplateEvaluator.DEBIT;
    private static final String C = TemplateEvaluator.CREDIT;

    public static final List<DefaultTemplate> TEMPLATES = List.of(
            new DefaultTemplate(PostingCase.POS_VENTA_CONTADO, "Venta de contado",
                    "El dinero entra a caja; el ingreso va sin IVA y el IVA a su cuenta por pagar.",
                    List.of(new DefaultLine(D, "110101", false, List.of("total"), "{descripcion}", true),
                            new DefaultLine(C, "410101", false, List.of("base"), "Ingresos {ref}", true),
                            new DefaultLine(C, "210201", false, List.of("iva"), "IVA {ref}", true))),
            new DefaultTemplate(PostingCase.POS_VENTA_CREDITO, "Venta al crédito",
                    "El cliente queda debiendo el total; el ingreso va sin IVA y el IVA a su cuenta por pagar.",
                    List.of(new DefaultLine(D, "110201", false, List.of("total"), "{descripcion}", true),
                            new DefaultLine(C, "410101", false, List.of("base"), "Ingresos {ref}", true),
                            new DefaultLine(C, "210201", false, List.of("iva"), "IVA {ref}", true))),
            // Anulación: el espejo de la venta. La partida de la venta no se toca.
            new DefaultTemplate(PostingCase.POS_ANULACION_CONTADO, "Anulación de venta de contado",
                    "Contrapartida de la venta anulada: sale el dinero de caja y se revierten el ingreso y el IVA.",
                    List.of(new DefaultLine(D, "410101", false, List.of("base"), "Anulación ingresos {ref}", true),
                            new DefaultLine(D, "210201", false, List.of("iva"), "Anulación IVA {ref}", true),
                            new DefaultLine(C, "110101", false, List.of("total"), "{descripcion}", true))),
            new DefaultTemplate(PostingCase.POS_ANULACION_CREDITO, "Anulación de venta al crédito",
                    "Contrapartida de la venta anulada: el cliente deja de deber y se revierten el ingreso y el IVA.",
                    List.of(new DefaultLine(D, "410101", false, List.of("base"), "Anulación ingresos {ref}", true),
                            new DefaultLine(D, "210201", false, List.of("iva"), "Anulación IVA {ref}", true),
                            new DefaultLine(C, "110201", false, List.of("total"), "{descripcion}", true))),
            new DefaultTemplate(PostingCase.CXC_COBRO, "Cobro de cliente",
                    "El dinero entra a la caja o al banco del cobro y baja lo que debe el cliente.",
                    List.of(new DefaultLine(D, "110101", true, List.of("monto"), "{descripcion}", false),
                            new DefaultLine(C, "110201", false, List.of("monto"), "{descripcion}", false))),
            new DefaultTemplate(PostingCase.PROYECTO_CONSUMO, "Consumo de material en proyecto",
                    "El material deja de ser inventario y pasa a costo, en el centro de costo del proyecto.",
                    List.of(new DefaultLine(D, "510101", false, List.of("costo"), "{descripcion}", true),
                            new DefaultLine(C, "110301", false, List.of("costo"), "{descripcion}", true))));
}
