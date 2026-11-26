package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.quote.dto.QuoteChargeDtos.PreviewLine;
import com.erp_maya.template.domain.ProductTemplate;
import com.erp_maya.template.service.TemplateCalculator;
import com.erp_maya.template.service.TemplateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vista previa con plantillas y por lotes ({@link ClientPricePreviewService}):
 * la línea de plantilla vale lo mismo que al agregarla a la cotización
 * (costo de materia prima × cantidad) y cada escenario se valora solo.
 */
class ClientPricePreviewTest {

    private static BigDecimal q(String v) { return new BigDecimal(v); }

    private static PreviewLine plantilla(String key, String cantidad, long templateId, Map<String, String> choices) {
        return new PreviewLine(key, cantidad == null ? null : q(cantidad), null, null, null, null, null,
                templateId, Map.of("ancho", q("4"), "profundidad", q("3")), choices, null);
    }

    /** Plantillas falsas: costo por plantilla y elección, sin base de datos. */
    private static final class Plantillas extends TemplateService {
        final List<Long> leidas = new ArrayList<>();
        final List<Map<String, String>> calculadas = new ArrayList<>();

        Plantillas() { super(null, null, null, null, null, null, null, null, null, null); }

        @Override
        public ProductTemplate buscar(Long id) {
            leidas.add(id);
            if (id == 99L) throw new ResourceNotFoundException("Plantilla 99 no encontrada");
            ProductTemplate t = new ProductTemplate();
            t.setId(id);
            return t;
        }

        @Override
        public TemplateCalculator.Result calcular(ProductTemplate t, Map<String, BigDecimal> pedidos,
                                                  Map<String, String> elegidas, String preset) {
            calculadas.add(elegidas);
            boolean teja = elegidas != null && "teja".equals(elegidas.get("cubierta"));
            boolean sinCosto = elegidas != null && "nueva".equals(elegidas.get("cubierta"));
            return new TemplateCalculator.Result(pedidos, elegidas, List.of(), teja ? q("9000.555") : q("6000"),
                    "Pérgola", "pérgola", sinCosto);
        }
    }

    // ── la línea de plantilla ────────────────────────────────────────────────

    @Test
    void laLineaDePlantillaValeElCostoDeMateriaPrimaPorCantidad() {
        PreviewLine l = ClientPricePreviewService.dePlantilla(plantilla("a", "2", 7L, Map.of()), q("6000.125"), false);
        assertEquals("a", l.key());
        assertEquals(q("2"), l.quantity());
        // Igual que QuoteTemplateService.llenar: costo × cantidad, a 2 decimales HALF_UP.
        assertEquals(q("12000.25"), l.lineTotal());
        // Sin producto ni medidas: el precio por medida no la vuelve a tocar.
        assertNull(l.productId());
        assertNull(l.width());
        assertNull(l.templateId());
    }

    @Test
    void sinCantidadSeCotizaUna() {
        assertEquals(q("6000.00"), ClientPricePreviewService.dePlantilla(plantilla("a", null, 7L, Map.of()), q("6000"), false).lineTotal());
        assertEquals(BigDecimal.ONE, ClientPricePreviewService.dePlantilla(plantilla("a", "0", 7L, Map.of()), q("6000"), false).quantity());
    }

    @Test
    void conCostosFaltantesQuedaEnCeroParaElAsesor() {
        assertEquals(q("0.00"), ClientPricePreviewService.dePlantilla(plantilla("a", "1", 7L, Map.of()), q("6000"), true).lineTotal());
        assertEquals(q("0.00"), ClientPricePreviewService.dePlantilla(plantilla("a", "1", 7L, Map.of()), BigDecimal.ZERO, false).lineTotal());
    }

    @Test
    void soloLasLineasConPlantillaSeCalculanYLaPlantillaSeLeeUnaVez() {
        Plantillas p = new Plantillas();
        var servicio = new ClientPricePreviewService(null, null, p);
        PreviewLine normal = new PreviewLine("n", q("3"), q("150.00"));
        var req = new QuoteChargeDtos.PreviewRequest(List.of(
                normal,
                plantilla("a", "1", 7L, Map.of("cubierta", "teja")),
                plantilla("b", "2", 7L, Map.of("cubierta", "policarbonato"))));
        var out = servicio.conPlantillas(req, new HashMap<>()).lines();
        assertSame(normal, out.get(0));
        assertEquals(q("9000.56"), out.get(1).lineTotal());
        assertEquals(q("12000.00"), out.get(2).lineTotal());
        assertEquals(List.of(7L), p.leidas, "la misma plantilla se lee una sola vez");
        assertEquals(2, p.calculadas.size());
    }

    @Test
    void sinPlantillasLaPeticionQuedaIgual() {
        var servicio = new ClientPricePreviewService(null, null, new Plantillas());
        var req = new QuoteChargeDtos.PreviewRequest(List.of(new PreviewLine("n", q("1"), q("10.00"))));
        assertSame(req, servicio.conPlantillas(req, new HashMap<>()));
    }

    @Test
    void conCostoFaltanteLaLineaDePlantillaVaEnCero() {
        var servicio = new ClientPricePreviewService(null, null, new Plantillas());
        var req = new QuoteChargeDtos.PreviewRequest(List.of(plantilla("a", "1", 7L, Map.of("cubierta", "nueva"))));
        assertEquals(q("0.00"), servicio.conPlantillas(req, new HashMap<>()).lines().get(0).lineTotal());
    }

    // ── por lotes ───────────────────────────────────────────────────────────

    /** Vista previa falsa: suma las líneas y le agrega un cargo fijo de Q 500. */
    private static QuoteChargeDtos.PreviewPrices conCargoFijo(QuoteChargeDtos.PreviewRequest r) {
        BigDecimal suma = r.lines().stream().map(PreviewLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.signum() < 0) throw new IllegalStateException("Monto negativo");
        BigDecimal subtotal = suma.add(q("500"));
        return new QuoteChargeDtos.PreviewPrices(subtotal, BigDecimal.ZERO, subtotal, List.of());
    }

    @Test
    void cadaEscenarioLlevaSusPropiosCargos() {
        var out = ClientPricePreviewService.escenarios(List.of(
                new QuoteChargeDtos.PreviewScenario("base", List.of(new PreviewLine("a", q("1"), q("1000")))),
                new QuoteChargeDtos.PreviewScenario("sin-luces", List.of(new PreviewLine("a", q("1"), q("800"))))),
                ClientPricePreviewTest::conCargoFijo);
        assertEquals("base", out.get(0).key());
        assertEquals(q("1500"), out.get(0).total());
        assertEquals(q("1300"), out.get(1).total(), "el cargo fijo se cobra en cada escenario, no se reparte");
        assertNull(out.get(1).error());
    }

    @Test
    void unEscenarioInvalidoNoTumbaALosDemas() {
        var out = ClientPricePreviewService.escenarios(List.of(
                new QuoteChargeDtos.PreviewScenario("malo", List.of(new PreviewLine("a", q("1"), q("-1")))),
                new QuoteChargeDtos.PreviewScenario("nulo", null),
                new QuoteChargeDtos.PreviewScenario("bueno", List.of(new PreviewLine("a", q("1"), q("100"))))),
                ClientPricePreviewTest::conCargoFijo);
        assertEquals("Monto negativo", out.get(0).error());
        assertNull(out.get(0).total());
        assertEquals(q("500"), out.get(1).total(), "sin líneas se valora vacío");
        assertEquals(q("600"), out.get(2).total());
    }

    @Test
    void unaPlantillaQueNoExisteQuedaComoErrorDelEscenario() {
        Plantillas p = new Plantillas();
        var servicio = new ClientPricePreviewService(null, null, p);
        var out = ClientPricePreviewService.escenarios(List.of(
                new QuoteChargeDtos.PreviewScenario("x", List.of(plantilla("a", "1", 99L, Map.of())))),
                r -> conCargoFijo(servicio.conPlantillas(r, new HashMap<>())));
        assertEquals("Plantilla 99 no encontrada", out.get(0).error());
    }

    @Test
    void elLoteTieneTope() {
        var servicio = new ClientPricePreviewService(null, null, new Plantillas());
        var muchos = IntStream.range(0, ClientPricePreviewService.MAX_ESCENARIOS + 1)
                .mapToObj(i -> new QuoteChargeDtos.PreviewScenario("e" + i, List.of())).toList();
        assertThrows(IllegalStateException.class,
                () -> servicio.batch(new QuoteChargeDtos.PreviewBatchRequest(muchos)));
    }
}
