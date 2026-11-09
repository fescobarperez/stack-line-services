package com.erp_maya.template.service;

import com.erp_maya.template.service.TemplateCalculator.Choice;
import com.erp_maya.template.service.TemplateCalculator.Component;
import com.erp_maya.template.service.TemplateCalculator.Cost;
import com.erp_maya.template.service.TemplateCalculator.Definition;
import com.erp_maya.template.service.TemplateCalculator.Option;
import com.erp_maya.template.service.TemplateCalculator.Param;
import com.erp_maya.template.service.TemplateCalculator.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Una pérgola con techo a elegir y cielo opcional ({@link TemplateCalculator}). */
class TemplateCalculatorTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static final Definition PERGOLA = new Definition("Pérgola",
            List.of(new Param("ancho", "Ancho", "m", d("1"), d("10"), null, true),
                    new Param("profundidad", "Profundidad", "m", d("1"), d("10"), null, true),
                    new Param("alto", "Alto", "m", d("2"), d("4"), d("2.5"), true)),
            List.of(new Option("techo", "Techo", true, List.of(
                            new Choice(10L, "policarbonato", "Policarbonato", "techo de policarbonato transparente", null, true),
                            new Choice(11L, "shingle", "Shingle", "techo de shingle gris sobre plywood", "más fresco", false))),
                    new Option("cielo", "Cielo", false, List.of(
                            new Choice(20L, "regletas", "Regletas", "cielo de regletas de madera", null, false)))),
            List.of(new Component(1L, null, 100L, "(ceil(ancho/1.5)+1)*2", BigDecimal.ZERO, true, "Estructura"),
                    new Component(2L, 11L, 101L, "ancho*profundidad", d("10"), false, "Techo"),
                    new Component(3L, 10L, 102L, "ancho*profundidad", d("5"), false, "Techo"),
                    new Component(4L, 20L, 103L, "ancho*profundidad", BigDecimal.ZERO, false, "Cielo")));

    private static final Map<Long, Cost> COSTOS = Map.of(
            100L, new Cost("Poste 4x4", "unid", 7L, d("85")),
            101L, new Cost("Shingle", "m2", 8L, d("60")),
            102L, new Cost("Policarbonato", "m2", 9L, d("95")),
            103L, new Cost("Regleta", "m2", null, null));

    private static Result calcular(Map<String, BigDecimal> params, Map<String, String> elecciones) {
        return TemplateCalculator.calcular(PERGOLA, params, elecciones, COSTOS::get);
    }

    @Test
    void calculaMateriaPrimaYTextos() {
        Result r = calcular(Map.of("ancho", d("3"), "profundidad", d("4.5")), Map.of("techo", "shingle", "cielo", "regletas"));
        assertEquals(3, r.lines().size());
        assertEquals(d("6"), r.lines().get(0).quantity());          // postes hacia arriba
        assertEquals(d("14.850"), r.lines().get(1).quantity());     // 13.5 m² + 10 % de merma
        assertEquals(d("891.00"), r.lines().get(1).amount());
        assertEquals(d("1401.00"), r.materialsCost());
        assertTrue(r.missingCosts());                                // las regletas no tienen costo
        assertEquals(d("2.5"), r.params().get("alto"));              // valor por defecto
        assertEquals("Pérgola 3 × 4.5 × 2.5 m · Techo: Shingle · Cielo: Regletas", r.description());
        assertEquals("Pérgola: techo de shingle gris sobre plywood; cielo de regletas de madera", r.renderText());
    }

    @Test
    void sinEleccionUsaLaDePorDefectoYOmiteLasOpcionales() {
        Result r = calcular(Map.of("ancho", d("3"), "profundidad", d("4.5")), Map.of());
        assertEquals(Map.of("techo", "policarbonato"), r.choices());
        assertEquals(2, r.lines().size());
    }

    @Test
    void validaRangosParametrosYElecciones() {
        assertTrue(assertThrows(IllegalStateException.class,
                () -> calcular(Map.of("ancho", d("30"), "profundidad", d("4")), Map.of()))
                .getMessage().contains("no puede pasar de 10 m"));
        assertThrows(IllegalStateException.class, () -> calcular(Map.of("ancho", d("3")), Map.of()));
        assertThrows(IllegalStateException.class,
                () -> calcular(Map.of("ancho", d("3"), "profundidad", d("4")), Map.of("techo", "teja")));
        assertThrows(IllegalStateException.class,
                () -> calcular(Map.of("ancho", d("3"), "profundidad", d("4"), "largo", d("1")), Map.of()));
    }
}
