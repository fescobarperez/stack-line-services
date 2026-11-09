package com.erp_maya.template.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fórmulas de las plantillas: aritmética sobre los parámetros del cliente.
 *
 *   (ceil(ancho / 1.5) + 1) * 2
 *   ancho * profundidad * 1.1
 *   max(alto, 2.4) * 4
 *
 * Operadores + - * / ^ y paréntesis; funciones ceil, floor, round(x[, d]),
 * min, max, abs, sqrt. Nada más: no es un lenguaje de scripts, así que una
 * plantilla mal escrita no puede ejecutar código. Una variable desconocida o
 * una división entre cero fallan con un mensaje que dice qué corregir.
 */
public final class Formula {

    static final MathContext MC = MathContext.DECIMAL64;
    static final int MAX_LARGO = 300;

    private final String texto;
    private final Map<String, BigDecimal> vars;
    private int pos;

    private Formula(String texto, Map<String, BigDecimal> vars) {
        this.texto = texto;
        this.vars = vars;
    }

    /** Evalúa la fórmula con esas variables. */
    public static BigDecimal evaluar(String formula, Map<String, BigDecimal> variables) {
        if (formula == null || formula.isBlank()) throw new IllegalStateException("La fórmula está vacía");
        if (formula.length() > MAX_LARGO) throw new IllegalStateException("La fórmula pasa de " + MAX_LARGO + " caracteres");
        Formula f = new Formula(formula, variables);
        BigDecimal v = f.expresion();
        f.espacios();
        if (f.pos < f.texto.length()) {
            throw f.error("sobra '" + f.texto.substring(f.pos) + "'");
        }
        return v;
    }

    /** Variables que usa la fórmula (para validar la plantilla al guardarla). */
    public static Set<String> variables(String formula) {
        Set<String> out = new LinkedHashSet<>();
        String s = formula == null ? "" : formula;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                int ini = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                String nombre = s.substring(ini, i);
                int j = i;
                while (j < s.length() && s.charAt(j) == ' ') j++;
                boolean esFuncion = j < s.length() && s.charAt(j) == '(';
                if (!esFuncion) out.add(nombre);
            } else {
                i++;
            }
        }
        return out;
    }

    /**
     * Revisa que la fórmula se pueda evaluar con esas variables (con valor 1).
     * Lanza IllegalStateException con el problema.
     */
    public static void validar(String formula, Set<String> permitidas) {
        for (String v : variables(formula)) {
            if (!permitidas.contains(v)) {
                throw new IllegalStateException("La fórmula «" + formula + "» usa '" + v
                        + "', que no es un parámetro de la plantilla (" + String.join(", ", permitidas) + ")");
            }
        }
        Map<String, BigDecimal> unos = new java.util.HashMap<>();
        permitidas.forEach(p -> unos.put(p, BigDecimal.ONE));
        try {
            evaluar(formula, unos);
        } catch (ArithmeticException e) {
            // Con todo en 1 una división puede dar cero (p. ej. 1/(ancho-1)); eso es válido.
        }
    }

    // ── gramática ──────────────────────────────────────────────────────
    // expresion := termino (('+'|'-') termino)*
    // termino   := potencia (('*'|'/') potencia)*
    // potencia  := unario ('^' potencia)?
    // unario    := '-' unario | primario
    // primario  := numero | nombre | nombre '(' args ')' | '(' expresion ')'

    private BigDecimal expresion() {
        BigDecimal v = termino();
        while (true) {
            espacios();
            if (acepta('+')) v = v.add(termino(), MC);
            else if (acepta('-')) v = v.subtract(termino(), MC);
            else return v;
        }
    }

    private BigDecimal termino() {
        BigDecimal v = potencia();
        while (true) {
            espacios();
            if (acepta('*')) v = v.multiply(potencia(), MC);
            else if (acepta('/')) {
                BigDecimal d = potencia();
                if (d.signum() == 0) throw new ArithmeticException("División entre cero en «" + texto + "»");
                v = v.divide(d, MC);
            } else return v;
        }
    }

    private BigDecimal potencia() {
        BigDecimal base = unario();
        espacios();
        if (acepta('^')) {
            BigDecimal exp = potencia();
            if (exp.stripTrailingZeros().scale() <= 0 && exp.abs().compareTo(BigDecimal.valueOf(100)) <= 0) {
                int n = exp.intValueExact();
                return n >= 0 ? base.pow(n, MC) : BigDecimal.ONE.divide(base.pow(-n, MC), MC);
            }
            return BigDecimal.valueOf(Math.pow(base.doubleValue(), exp.doubleValue()));
        }
        return base;
    }

    private BigDecimal unario() {
        espacios();
        if (acepta('-')) return unario().negate();
        if (acepta('+')) return unario();
        return primario();
    }

    private BigDecimal primario() {
        espacios();
        if (pos >= texto.length()) throw error("termina antes de tiempo");
        char c = texto.charAt(pos);
        if (acepta('(')) {
            BigDecimal v = expresion();
            espacios();
            if (!acepta(')')) throw error("falta ')'");
            return v;
        }
        if (Character.isDigit(c) || c == '.') return numero();
        if (Character.isLetter(c) || c == '_') {
            String nombre = nombre();
            espacios();
            if (acepta('(')) return funcion(nombre.toLowerCase(), argumentos());
            BigDecimal v = vars.get(nombre);
            if (v == null) throw error("no conozco la variable '" + nombre + "'");
            return v;
        }
        throw error("no esperaba '" + c + "'");
    }

    private List<BigDecimal> argumentos() {
        List<BigDecimal> args = new ArrayList<>();
        espacios();
        if (acepta(')')) return args;
        do {
            args.add(expresion());
            espacios();
        } while (acepta(','));
        if (!acepta(')')) throw error("falta ')' en la función");
        return args;
    }

    private BigDecimal funcion(String nombre, List<BigDecimal> a) {
        return switch (nombre) {
            case "ceil" -> uno(nombre, a).setScale(0, RoundingMode.CEILING);
            case "floor" -> uno(nombre, a).setScale(0, RoundingMode.FLOOR);
            case "abs" -> uno(nombre, a).abs();
            case "sqrt" -> {
                BigDecimal x = uno(nombre, a);
                if (x.signum() < 0) throw new ArithmeticException("Raíz de un negativo en «" + texto + "»");
                yield x.sqrt(MC);
            }
            case "round" -> {
                if (a.size() == 1) yield a.get(0).setScale(0, RoundingMode.HALF_UP);
                if (a.size() == 2) yield a.get(0).setScale(a.get(1).intValue(), RoundingMode.HALF_UP);
                throw error("round lleva 1 o 2 argumentos");
            }
            case "min" -> {
                if (a.isEmpty()) throw error("min necesita argumentos");
                yield a.stream().reduce(BigDecimal::min).orElseThrow();
            }
            case "max" -> {
                if (a.isEmpty()) throw error("max necesita argumentos");
                yield a.stream().reduce(BigDecimal::max).orElseThrow();
            }
            default -> throw error("no conozco la función '" + nombre + "' (ceil, floor, round, min, max, abs, sqrt)");
        };
    }

    private BigDecimal uno(String nombre, List<BigDecimal> a) {
        if (a.size() != 1) throw error(nombre + " lleva un argumento");
        return a.get(0);
    }

    private BigDecimal numero() {
        int ini = pos;
        while (pos < texto.length() && (Character.isDigit(texto.charAt(pos)) || texto.charAt(pos) == '.')) pos++;
        try {
            return new BigDecimal(texto.substring(ini, pos));
        } catch (NumberFormatException e) {
            throw error("número inválido '" + texto.substring(ini, pos) + "'");
        }
    }

    private String nombre() {
        int ini = pos;
        while (pos < texto.length() && (Character.isLetterOrDigit(texto.charAt(pos)) || texto.charAt(pos) == '_')) pos++;
        return texto.substring(ini, pos);
    }

    private void espacios() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) pos++;
    }

    private boolean acepta(char c) {
        if (pos < texto.length() && texto.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private IllegalStateException error(String que) {
        return new IllegalStateException("Fórmula «" + texto + "»: " + que);
    }
}
