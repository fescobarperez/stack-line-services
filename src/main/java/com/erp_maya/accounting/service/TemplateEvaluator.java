package com.erp_maya.accounting.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Convierte una plantilla de partida en líneas concretas, y valida que una
 * plantilla tenga sentido para su caso de uso.
 *
 * Es lógica pura —sin base de datos— para poder probarla sola: aquí se decide
 * qué cuenta y qué monto lleva cada renglón, que es justo lo que no puede
 * salir mal.
 */
public final class TemplateEvaluator {

    public static final String DEBIT = "debit";
    public static final String CREDIT = "credit";

    private TemplateEvaluator() {}

    /** Una línea de plantilla, sin JPA de por medio. */
    public record LineSpec(String side, Long accountId, boolean usesDocumentAccount,
                           List<String> amounts, String description, boolean useCostCenter) {}

    /** Lo que trae la operación: sus montos, la cuenta del documento y los textos. */
    public record Context(Map<String, BigDecimal> amounts, Long documentAccountId, Long costCenterId,
                          String descripcion, String ref, String tercero) {}

    /**
     * Las líneas de la partida. La cuenta del documento gana sobre la fija; la
     * fija es el respaldo. Una línea sin ninguna de las dos es un error de
     * configuración y se dice cuál.
     */
    public static List<PostingService.Line> evaluate(List<LineSpec> lines, Context ctx) {
        List<PostingService.Line> out = new ArrayList<>();
        int n = 0;
        for (LineSpec l : lines) {
            n++;
            BigDecimal monto = amount(l, ctx.amounts());
            Long cuenta = l.usesDocumentAccount() && ctx.documentAccountId() != null
                    ? ctx.documentAccountId() : l.accountId();
            if (cuenta == null) {
                throw new IllegalStateException("La línea " + n + " de la plantilla usa la cuenta del documento "
                        + "y esta operación no trae ninguna. Ponle una cuenta de respaldo en "
                        + "Contabilidad → Partidas automáticas → Plantillas de partidas.");
            }
            boolean debe = DEBIT.equals(l.side());
            out.add(new PostingService.Line(null, cuenta,
                    debe ? monto : BigDecimal.ZERO,
                    debe ? BigDecimal.ZERO : monto,
                    text(l.description(), ctx),
                    l.useCostCenter() ? ctx.costCenterId() : null));
        }
        return out;
    }

    /** La suma de los montos que pide la línea. Un monto que el caso no trae cuenta como cero. */
    public static BigDecimal amount(LineSpec l, Map<String, BigDecimal> amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (String k : l.amounts()) {
            BigDecimal v = amounts.get(k);
            if (v != null) total = total.add(v);
        }
        return total;
    }

    /** Débitos menos créditos de unas líneas ya evaluadas. */
    public static BigDecimal difference(List<PostingService.Line> lines) {
        BigDecimal d = BigDecimal.ZERO;
        for (PostingService.Line l : lines) d = d.add(l.debit()).subtract(l.credit());
        return d;
    }

    /**
     * Qué está mal en una plantilla para su caso de uso. Vacía si está bien.
     * Lo último que se revisa es que cuadre con el ejemplo del caso: si no
     * cuadra ahí, no cuadra con ninguna operación real.
     */
    public static List<String> validate(PostingCase useCase, List<LineSpec> lines) {
        List<String> errores = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            errores.add("La plantilla necesita al menos una línea al débito y una al crédito.");
            return errores;
        }
        boolean hayDebe = false, hayHaber = false;
        int n = 0;
        for (LineSpec l : lines) {
            n++;
            if (DEBIT.equals(l.side())) hayDebe = true;
            else if (CREDIT.equals(l.side())) hayHaber = true;
            else errores.add("Línea " + n + ": indica si va al débito o al crédito.");

            if (l.usesDocumentAccount() && !useCase.hasDocumentAccount()) {
                errores.add("Línea " + n + ": «" + useCase.label() + "» no trae cuenta del documento; elige una cuenta.");
            }
            if (!l.usesDocumentAccount() && l.accountId() == null) {
                errores.add("Línea " + n + ": elige la cuenta.");
            }
            if (l.amounts() == null || l.amounts().isEmpty()) {
                errores.add("Línea " + n + ": elige el monto.");
            } else {
                for (String k : l.amounts()) {
                    if (!useCase.hasAmount(k)) {
                        errores.add("Línea " + n + ": «" + useCase.label() + "» no tiene el monto «" + k + "».");
                    }
                }
            }
        }
        if (!hayDebe || !hayHaber) {
            errores.add("La plantilla necesita al menos una línea al débito y una al crédito.");
        }
        if (errores.isEmpty()) {
            BigDecimal debe = BigDecimal.ZERO, haber = BigDecimal.ZERO;
            for (LineSpec l : lines) {
                BigDecimal m = amount(l, useCase.example());
                if (DEBIT.equals(l.side())) debe = debe.add(m); else haber = haber.add(m);
            }
            if (debe.compareTo(haber) != 0) {
                errores.add("La plantilla no cuadra: con montos de ejemplo da débitos " + debe.toPlainString()
                        + " contra créditos " + haber.toPlainString() + ". Revisa qué monto lleva cada línea.");
            }
        }
        return errores;
    }

    /** Reemplaza {descripcion}, {ref} y {tercero}. Sin texto, la línea lleva la descripción de la partida. */
    public static String text(String pattern, Context ctx) {
        if (pattern == null || pattern.isBlank()) return ctx.descripcion();
        String s = pattern
                .replace("{descripcion}", nn(ctx.descripcion()))
                .replace("{ref}", nn(ctx.ref()))
                .replace("{tercero}", nn(ctx.tercero()))
                .trim();
        // La columna de la línea admite 200 caracteres.
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static String nn(String s) { return s == null ? "" : s; }
}
