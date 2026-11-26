package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.template.domain.ProductTemplate;
import com.erp_maya.template.service.TemplateCalculator;
import com.erp_maya.template.service.TemplateService;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Vista previa de precios al cliente sin cotización: el carrito del chat web
 * y el planificador por presupuesto.
 *
 * Arma la línea igual que la cotización —plantilla de cálculo, precio por
 * medida— y luego aplica cargos, ganancia, IVA y redondeo de la empresa con
 * {@link QuoteChargeService#previewClientPrices}. Así el total previo y el de
 * la cotización salen de la misma fórmula. No guarda nada.
 *
 * Vive aparte de {@link QuoteService} para no hacerlo depender de las
 * plantillas (las plantillas ya dependen de las cotizaciones).
 */
@Singleton
public class ClientPricePreviewService {

    /** Escenarios por consulta: una opción y sus variantes. */
    static final int MAX_ESCENARIOS = 10;

    private final QuoteService quotes;
    private final QuoteChargeService charges;
    private final TemplateService templates;

    public ClientPricePreviewService(QuoteService quotes, QuoteChargeService charges, TemplateService templates) {
        this.quotes = quotes;
        this.charges = charges;
        this.templates = templates;
    }

    public QuoteChargeDtos.PreviewPrices preview(QuoteChargeDtos.PreviewRequest req) {
        return valorar(req, new HashMap<>());
    }

    /**
     * Cada escenario se valora por separado (sus cargos fijos se cobran en
     * cada uno). Uno que no se pueda valorar —plantilla apagada, medida fuera
     * de rango— vuelve con su `error` y no tumba a los demás.
     */
    public QuoteChargeDtos.PreviewBatchResponse batch(QuoteChargeDtos.PreviewBatchRequest req) {
        List<QuoteChargeDtos.PreviewScenario> lista = req == null || req.scenarios() == null ? List.of() : req.scenarios();
        if (lista.size() > MAX_ESCENARIOS) {
            throw new IllegalStateException("Máximo " + MAX_ESCENARIOS + " escenarios por consulta");
        }
        // Las plantillas se leen una vez para todo el lote.
        Map<Long, ProductTemplate> cache = new HashMap<>();
        return new QuoteChargeDtos.PreviewBatchResponse(escenarios(lista, r -> valorar(r, cache)));
    }

    private QuoteChargeDtos.PreviewPrices valorar(QuoteChargeDtos.PreviewRequest req, Map<Long, ProductTemplate> cache) {
        return charges.previewClientPrices(quotes.conPrecioPorMedida(conPlantillas(req, cache)));
    }

    /** Las líneas con plantilla pasan a su costo de materia prima × cantidad; las demás quedan igual. */
    QuoteChargeDtos.PreviewRequest conPlantillas(QuoteChargeDtos.PreviewRequest req, Map<Long, ProductTemplate> cache) {
        if (req == null || req.lines() == null || req.lines().stream().noneMatch(l -> l.templateId() != null)) return req;
        List<QuoteChargeDtos.PreviewLine> out = new ArrayList<>();
        for (QuoteChargeDtos.PreviewLine l : req.lines()) {
            if (l.templateId() == null) {
                out.add(l);
                continue;
            }
            ProductTemplate t = cache.computeIfAbsent(l.templateId(), templates::buscar);
            TemplateCalculator.Result r = templates.calcular(t, l.params(), l.choices(), l.preset());
            out.add(dePlantilla(l, r.materialsCost(), r.missingCosts()));
        }
        return new QuoteChargeDtos.PreviewRequest(out);
    }

    /**
     * La línea ya calculada con la plantilla: como en la cotización, el precio
     * base es el costo de la materia prima. Sin producto ni medidas, para que
     * el precio por medida no la vuelva a tocar. Si falta algún costo, va en 0:
     * la vista previa la marca `review` y la cotiza un asesor.
     */
    static QuoteChargeDtos.PreviewLine dePlantilla(QuoteChargeDtos.PreviewLine l, BigDecimal costoMateriales,
                                                   boolean faltanCostos) {
        BigDecimal cantidad = l.quantity() == null || l.quantity().signum() <= 0 ? BigDecimal.ONE : l.quantity();
        BigDecimal total = faltanCostos || costoMateriales == null || costoMateriales.signum() <= 0
                ? BigDecimal.ZERO.setScale(2)
                : costoMateriales.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
        return new QuoteChargeDtos.PreviewLine(l.key(), cantidad, total);
    }

    /** Valora cada escenario con `precio`; los errores de negocio quedan en su escenario. */
    static List<QuoteChargeDtos.ScenarioPrices> escenarios(
            List<QuoteChargeDtos.PreviewScenario> lista,
            Function<QuoteChargeDtos.PreviewRequest, QuoteChargeDtos.PreviewPrices> precio) {
        List<QuoteChargeDtos.ScenarioPrices> out = new ArrayList<>();
        for (QuoteChargeDtos.PreviewScenario s : lista) {
            List<QuoteChargeDtos.PreviewLine> lineas = s.lines() == null ? List.of() : s.lines();
            try {
                QuoteChargeDtos.PreviewPrices p = precio.apply(new QuoteChargeDtos.PreviewRequest(lineas));
                out.add(new QuoteChargeDtos.ScenarioPrices(s.key(), p.subtotal(), p.tax(), p.total(), p.lines(), null));
            } catch (IllegalStateException | ResourceNotFoundException e) {
                out.add(new QuoteChargeDtos.ScenarioPrices(s.key(), null, null, null, List.of(), e.getMessage()));
            }
        }
        return out;
    }
}
