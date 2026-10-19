package com.erp_maya.template.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * El cálculo de una plantilla, sin base de datos: parámetros y elecciones del
 * cliente → materia prima con cantidad y costo, descripción de la línea y
 * texto del render. Solo materiales: la mano de obra y los demás costos se
 * agregan en la cotización.
 *
 *   cantidad = fórmula × (1 + merma %)   → hacia arriba si el componente lo pide
 *   costo    = cantidad × costo unitario del proveedor preferido
 *
 * Todo lo que falte o esté mal (un parámetro fuera de rango, una elección que
 * no existe, una fórmula que divide entre cero) falla con un mensaje claro:
 * en un cotizador, un número dudoso es peor que un error.
 */
public final class TemplateCalculator {

    private TemplateCalculator() {
    }

    public record Param(String code, String label, String unit, BigDecimal min, BigDecimal max,
                        BigDecimal defaultValue, boolean required) {}

    public record Choice(Long id, String code, String label, String renderText, String benefit, boolean isDefault) {}

    public record Option(String code, String label, boolean required, List<Choice> choices) {}

    public record Component(Long id, Long choiceId, Long productId, String formula, BigDecimal wastePct,
                            boolean roundUp, String group) {}

    public record Definition(String productName, List<Param> params, List<Option> options,
                             List<Component> components) {}

    /** Costo de una materia prima: proveedor preferido (o null) y costo por unidad de uso. */
    public record Cost(String productName, String unit, Long supplierId, BigDecimal unitCost) {}

    public record Line(Long componentId, Long productId, String productName, String unit, String group,
                       BigDecimal quantity, Long supplierId, BigDecimal unitCost, BigDecimal amount,
                       boolean missingCost) {}

    public record Result(Map<String, BigDecimal> params, Map<String, String> choices, List<Line> lines,
                         BigDecimal materialsCost, String description, String renderText, boolean missingCosts) {}

    public static Result calcular(Definition def, Map<String, BigDecimal> pedidos, Map<String, String> elegidas,
                                  Function<Long, Cost> costos) {
        Map<String, BigDecimal> params = resolverParametros(def.params(), pedidos == null ? Map.of() : pedidos);
        Map<String, Choice> escogidas = resolverElecciones(def.options(), elegidas == null ? Map.of() : elegidas);
        java.util.Set<Long> idsEscogidos = new java.util.HashSet<>();
        escogidas.values().forEach(c -> idsEscogidos.add(c.id()));

        List<Line> lineas = new ArrayList<>();
        BigDecimal materiales = BigDecimal.ZERO;
        boolean faltanCostos = false;
        for (Component comp : def.components()) {
            if (comp.choiceId() != null && !idsEscogidos.contains(comp.choiceId())) continue;
            BigDecimal cantidad = evaluar(comp.formula(), params);
            if (cantidad.signum() < 0) {
                throw new IllegalStateException("La fórmula «" + comp.formula() + "» dio una cantidad negativa");
            }
            if (cantidad.signum() == 0) continue; // p. ej. canal = 0 con un agua
            BigDecimal merma = comp.wastePct() == null ? BigDecimal.ZERO : comp.wastePct();
            cantidad = cantidad.multiply(BigDecimal.ONE.add(merma.movePointLeft(2)));
            cantidad = comp.roundUp() ? cantidad.setScale(0, RoundingMode.CEILING) : cantidad.setScale(3, RoundingMode.HALF_UP);
            Cost costo = costos.apply(comp.productId());
            if (costo == null) {
                throw new IllegalStateException("La materia prima " + comp.productId() + " de la plantilla no existe");
            }
            boolean sinCosto = costo.unitCost() == null || costo.unitCost().signum() <= 0;
            BigDecimal unitario = sinCosto ? BigDecimal.ZERO : costo.unitCost();
            BigDecimal monto = cantidad.multiply(unitario).setScale(2, RoundingMode.HALF_UP);
            faltanCostos |= sinCosto;
            materiales = materiales.add(monto);
            lineas.add(new Line(comp.id(), comp.productId(), costo.productName(), costo.unit(),
                    comp.group() == null || comp.group().isBlank() ? "Materiales" : comp.group(),
                    cantidad, costo.supplierId(), unitario, monto, sinCosto));
        }

        Map<String, String> codigos = new LinkedHashMap<>();
        escogidas.forEach((op, c) -> codigos.put(op, c.code()));
        return new Result(params, codigos, lineas, materiales.setScale(2, RoundingMode.HALF_UP),
                descripcion(def, params, escogidas), textoRender(def, params, escogidas), faltanCostos);
    }

    /** Valores pedidos, con el defecto si faltan; valida obligatorio y rango. */
    static Map<String, BigDecimal> resolverParametros(List<Param> definidos, Map<String, BigDecimal> pedidos) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Param p : definidos) {
            BigDecimal v = pedidos.get(p.code());
            if (v == null) v = p.defaultValue();
            if (v == null) {
                if (p.required()) throw new IllegalStateException("Falta " + p.label() + " (" + p.code() + ")");
                v = BigDecimal.ZERO;
            }
            if (p.min() != null && v.compareTo(p.min()) < 0) {
                throw new IllegalStateException(p.label() + " debe ser al menos " + plano(p.min()) + unidad(p));
            }
            if (p.max() != null && v.compareTo(p.max()) > 0) {
                throw new IllegalStateException(p.label() + " no puede pasar de " + plano(p.max()) + unidad(p));
            }
            out.put(p.code(), v);
        }
        for (String k : pedidos.keySet()) {
            if (!out.containsKey(k)) throw new IllegalStateException("La plantilla no tiene el parámetro '" + k + "'");
        }
        return out;
    }

    /** Elección por opción: la pedida, o la de por defecto; obligatoria si la opción lo es. */
    static Map<String, Choice> resolverElecciones(List<Option> opciones, Map<String, String> pedidas) {
        Map<String, Choice> out = new LinkedHashMap<>();
        Map<String, Option> porCodigo = new HashMap<>();
        opciones.forEach(o -> porCodigo.put(o.code(), o));
        for (String k : pedidas.keySet()) {
            if (!porCodigo.containsKey(k)) throw new IllegalStateException("La plantilla no tiene la opción '" + k + "'");
        }
        for (Option o : opciones) {
            String pedida = pedidas.get(o.code());
            Choice c = null;
            if (pedida != null && !pedida.isBlank()) {
                c = o.choices().stream().filter(x -> x.code().equals(pedida)).findFirst()
                        .orElseThrow(() -> new IllegalStateException(o.label() + ": no existe la opción '" + pedida
                                + "' (" + String.join(", ", o.choices().stream().map(Choice::code).toList()) + ")"));
            } else {
                c = o.choices().stream().filter(Choice::isDefault).findFirst().orElse(null);
            }
            if (c == null && o.required()) throw new IllegalStateException("Falta elegir " + o.label());
            if (c != null) out.put(o.code(), c);
        }
        return out;
    }

    /** "Pérgola 3 × 4.5 × 2.5 m · Techo: Shingle · Cielo: Regletas". */
    static String descripcion(Definition def, Map<String, BigDecimal> params, Map<String, Choice> escogidas) {
        StringBuilder sb = new StringBuilder(def.productName());
        List<String> medidas = new ArrayList<>();
        String unidadMedida = null;
        for (Param p : def.params()) {
            if (p.unit() != null && p.unit().equalsIgnoreCase("m")) {
                medidas.add(plano(params.get(p.code())));
                unidadMedida = "m";
            }
        }
        if (!medidas.isEmpty()) sb.append(' ').append(String.join(" × ", medidas)).append(' ').append(unidadMedida);
        for (Option o : def.options()) {
            Choice c = escogidas.get(o.code());
            if (c != null) sb.append(" · ").append(o.label()).append(": ").append(c.label());
        }
        String s = sb.toString();
        return s.length() > 300 ? s.substring(0, 297) + "…" : s;
    }

    /** Lo que el render debe dibujar: el producto, medidas y cada elección con su texto. */
    static String textoRender(Definition def, Map<String, BigDecimal> params, Map<String, Choice> escogidas) {
        List<String> partes = new ArrayList<>();
        for (Option o : def.options()) {
            Choice c = escogidas.get(o.code());
            if (c == null) continue;
            partes.add(c.renderText() != null && !c.renderText().isBlank() ? c.renderText() : o.label() + ": " + c.label());
        }
        return def.productName() + (partes.isEmpty() ? "" : ": " + String.join("; ", partes));
    }

    private static BigDecimal evaluar(String formula, Map<String, BigDecimal> params) {
        try {
            return Formula.evaluar(formula, params);
        } catch (ArithmeticException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    static String plano(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String unidad(Param p) {
        return p.unit() == null || p.unit().isBlank() ? "" : " " + p.unit();
    }
}
