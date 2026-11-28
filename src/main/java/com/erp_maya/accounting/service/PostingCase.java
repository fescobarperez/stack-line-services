package com.erp_maya.accounting.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Los puntos del sistema que generan una partida automática.
 *
 * El catálogo vive en el código a propósito: cada caso sabe qué montos produce
 * (la venta sabe su total, su base y su IVA) y eso no es configurable. Lo que
 * decide la empresa es a qué cuentas van, con una plantilla asociada al caso
 * (Contabilidad → Partidas automáticas). Ver docs/partidas-automaticas/SPEC.md.
 *
 * El ejemplo de cada caso cumple sus invariantes (total = base + IVA) y sirve
 * para validar que una plantilla cuadra antes de guardarla.
 */
public enum PostingCase {

    POS_VENTA_CONTADO("pos.venta.contado", "Venta POS de contado", "Ventas",
            List.of(new Amount("total", "Total"), new Amount("base", "Base sin IVA"), new Amount("iva", "IVA")),
            null,
            ejemplo("total", "112.00", "base", "100.00", "iva", "12.00")),

    POS_VENTA_CREDITO("pos.venta.credito", "Venta POS al crédito", "Ventas",
            List.of(new Amount("total", "Total"), new Amount("base", "Base sin IVA"), new Amount("iva", "IVA")),
            null,
            ejemplo("total", "112.00", "base", "100.00", "iva", "12.00")),

    /**
     * Contrapartida de una venta anulada. La partida de la venta se conserva
     * (es un hecho) y esta la compensa; por defecto es su espejo, pero se
     * configura como cualquier otra (p. ej. a una cuenta de devoluciones).
     */
    POS_ANULACION_CONTADO("pos.anulacion.contado", "Anulación de venta de contado", "Ventas",
            List.of(new Amount("total", "Total"), new Amount("base", "Base sin IVA"), new Amount("iva", "IVA")),
            null,
            ejemplo("total", "112.00", "base", "100.00", "iva", "12.00")),

    POS_ANULACION_CREDITO("pos.anulacion.credito", "Anulación de venta al crédito", "Ventas",
            List.of(new Amount("total", "Total"), new Amount("base", "Base sin IVA"), new Amount("iva", "IVA")),
            null,
            ejemplo("total", "112.00", "base", "100.00", "iva", "12.00")),

    CXC_COBRO("cxc.cobro", "Cobro de cliente", "Cobros y pagos",
            List.of(new Amount("monto", "Monto cobrado")),
            "Caja o banco donde entró el dinero",
            ejemplo("monto", "100.00")),

    PROYECTO_CONSUMO("proyecto.consumo", "Consumo de material en proyecto", "Proyectos",
            List.of(new Amount("costo", "Costo del material")),
            null,
            ejemplo("costo", "100.00"));

    /** Un monto que el caso de uso entrega a la plantilla. */
    public record Amount(String key, String label) {}

    private final String code;
    private final String label;
    private final String group;
    private final List<Amount> amounts;
    private final String documentAccount;
    private final Map<String, BigDecimal> example;

    PostingCase(String code, String label, String group, List<Amount> amounts,
                String documentAccount, Map<String, BigDecimal> example) {
        this.code = code;
        this.label = label;
        this.group = group;
        this.amounts = amounts;
        this.documentAccount = documentAccount;
        this.example = example;
    }

    public String code() { return code; }
    public String label() { return label; }
    public String group() { return group; }
    public List<Amount> amounts() { return amounts; }

    /** Qué cuenta trae el documento, o null si el caso no trae ninguna. */
    public String documentAccount() { return documentAccount; }

    public boolean hasDocumentAccount() { return documentAccount != null; }

    public Map<String, BigDecimal> example() { return example; }

    public boolean hasAmount(String key) {
        return amounts.stream().anyMatch(a -> a.key().equals(key));
    }

    public static Optional<PostingCase> byCode(String code) {
        return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst();
    }

    private static Map<String, BigDecimal> ejemplo(String... pares) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put(pares[i], new BigDecimal(pares[i + 1]));
        return Map.copyOf(m);
    }
}
