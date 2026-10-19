package com.erp_maya.template.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fórmulas de las plantillas ({@link Formula}). */
class FormulaTest {

    private static final Map<String, BigDecimal> V = Map.of(
            "ancho", new BigDecimal("3"), "profundidad", new BigDecimal("4.5"), "alto", new BigDecimal("2.5"));

    private static void igual(String esperado, String formula) {
        assertEquals(0, new BigDecimal(esperado).compareTo(Formula.evaluar(formula, V)), formula);
    }

    @Test
    void aritmeticaYFunciones() {
        igual("6", "(ceil(ancho/1.5)+1)*2");
        igual("14.85", "ancho*profundidad*1.1");
        igual("10", "max(alto, 2.4) * 4");
        igual("10", "-2 + 3 * 2 ^ 2");
        igual("3.33", "round(10/3, 2)");
        igual("0.5", "2^-1");
        igual("2", "min(floor(2.9), 7)");
    }

    @Test
    void listaLasVariablesSinLasFunciones() {
        assertEquals(List.of("ancho", "profundidad", "x"),
                List.copyOf(Formula.variables("ceil(ancho/1.5) + profundidad*x")));
    }

    @Test
    void erroresConMensajeClaro() {
        assertTrue(assertThrows(IllegalStateException.class, () -> Formula.evaluar("ancho * largo", V))
                .getMessage().contains("largo"));
        assertThrows(IllegalStateException.class, () -> Formula.evaluar("ancho +", V));
        assertThrows(IllegalStateException.class, () -> Formula.evaluar("ancho ) 2", V));
        assertThrows(IllegalStateException.class, () -> Formula.evaluar("system(1)", V));
        assertThrows(IllegalStateException.class, () -> Formula.evaluar(" ", V));
        assertThrows(ArithmeticException.class, () -> Formula.evaluar("1/(ancho-3)", V));
    }

    @Test
    void validarRechazaVariablesQueNoSonParametros() {
        assertTrue(assertThrows(IllegalStateException.class, () -> Formula.validar("ancho * largo", Set.of("ancho")))
                .getMessage().contains("largo"));
        Formula.validar("ancho * 2", Set.of("ancho"));
    }
}
