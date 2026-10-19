package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.settings.repository.CompanySettingRepository;
import com.erp_maya.quote.domain.ChargeCategory;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteCharge;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.quote.repository.ChargeCategoryRepository;
import com.erp_maya.quote.repository.QuoteChargeRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.settings.service.TaxService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gastos/cargos de la cotización y el cálculo de su total.
 *
 * El gasto de materiales es DERIVADO (Σ costo de los materiales de la
 * cotización) y no se persiste. Los cargos manuales (fixed/percent) sí. Los
 * percent se aplican sobre el subtotal de costo = materiales + fixed.
 */
@Singleton
public class QuoteChargeService {

    private final QuoteChargeRepository charges;
    private final ChargeCategoryRepository categories;
    private final QuoteRepository quotes;
    private final Materials materials;
    private final TenantContext tenant;
    private final CompanySettingRepository settings;
    private final ObjectMapper json;

    /**
     * Cargos que se copian a cada cotización nueva (Configuración ›
     * Cotizaciones › Cargos por defecto). JSON en company_settings:
     * [{"categoryId": 3, "description": null, "calcType": "percent", "value": 10},
     *  {"kind": "profit", "calcType": "percent", "value": 30}].
     * Se COPIAN al crear la cotización: cambiar la configuración no toca las
     * que ya existen, y el asesor puede editarlos o quitarlos en cada una.
     * La fila "profit" no es un cargo: llena la ganancia de la cotización.
     */
    public static final String CLAVE_CARGOS_DEFECTO = "quotes.default_charges";

    @Serdeable
    public record CargoDefecto(String kind, Long categoryId, String description, String calcType, BigDecimal value) {
        boolean esGanancia() { return "profit".equalsIgnoreCase(kind); }
    }

    /** Ganancia con la que nace una cotización: tipo ('percent' | 'fixed') y valor. */
    public record GananciaDefecto(String calcType, BigDecimal value) {}

    /** Clave anterior de la ganancia sugerida (solo %); se lee si la lista no trae ganancia. */
    static final String CLAVE_GANANCIA_ANTERIOR = "quotes.default_profit_pct";

    /**
     * Base de la ganancia porcentual (company_settings quotes.profit_base):
     * costo total = subtotal + gastos + cargos (como siempre); costo directo =
     * solo el subtotal, para quien suma maquinaria, traslados y ganancia como
     * porcentajes del mismo costo, sin encadenarlos.
     */
    public static final String BASE_COSTO_TOTAL = "total_cost";
    public static final String BASE_COSTO_DIRECTO = "direct_cost";
    private final TaxService taxService;

    public QuoteChargeService(QuoteChargeRepository charges, ChargeCategoryRepository categories,
                              QuoteRepository quotes,
                              Materials materials, TenantContext tenant, TaxService taxService,
                              CompanySettingRepository settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json;
        this.charges = charges;
        this.categories = categories;
        this.quotes = quotes;
        this.materials = materials;
        this.tenant = tenant;
        this.taxService = taxService;
    }

    @Transactional
    public QuoteChargeDtos.Summary getSummary(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        return recompute(quoteId, companyId);
    }

    /**
     * Copia los cargos por defecto de la empresa a una cotización recién
     * creada y recalcula. Si hay alguno, la cotización queda en modo
     * 'detailed': en 'percent' los cargos operativos capturados no suman y los
     * copiados no se verían en el total.
     */
    @Transactional
    public QuoteChargeDtos.Summary aplicarCargosPorDefecto(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        Quote quote = quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        Map<Long, ChargeCategory> catalogo = categories.findByCompanyIdOrderBySortOrderAsc(companyId)
                .stream().collect(Collectors.toMap(ChargeCategory::getId, Function.identity()));
        Set<Long> activos = catalogo.values().stream()
                .filter(c -> !"inactive".equalsIgnoreCase(c.getStatus()))
                .map(ChargeCategory::getId).collect(Collectors.toSet());
        List<CargoDefecto> copiar = aplicables(cargosPorDefecto(companyId), activos);
        int orden = 10;
        for (CargoDefecto d : copiar) {
            ChargeCategory cat = catalogo.get(d.categoryId());
            QuoteCharge c = new QuoteCharge();
            c.setCompanyId(companyId);
            c.setQuoteId(quoteId);
            c.setCategoryId(cat.getId());
            c.setDescription(d.description() != null && !d.description().isBlank() ? d.description().trim() : cat.getName());
            c.setCalcType("percent".equalsIgnoreCase(d.calcType()) ? "percent" : "fixed");
            c.setValue(d.value());
            c.setSortOrder(orden++);
            charges.save(c);
        }
        if (!copiar.isEmpty() && !"detailed".equals(quote.getOperatingExpenseMode())) {
            quote.setOperatingExpenseMode("detailed");
            quotes.update(quote);
        }
        return recompute(quoteId, companyId);
    }

    /**
     * Ganancia por defecto: la fila "profit" de los cargos por defecto; si no
     * hay, la clave anterior (quotes.default_profit_pct, en %). Vacío = la
     * cotización nace sin ganancia sugerida.
     */
    public java.util.Optional<GananciaDefecto> gananciaPorDefecto(Long companyId) {
        java.util.Optional<GananciaDefecto> deLista = ganancia(cargosPorDefecto(companyId));
        if (deLista.isPresent()) return deLista;
        return settings.findByCompanyIdAndSettingKey(companyId, CLAVE_GANANCIA_ANTERIOR)
                .map(c -> c.getSettingValue())
                .filter(v -> v != null && v.trim().matches("\\d{1,4}(\\.\\d{1,4})?"))
                .map(v -> new GananciaDefecto("percent", new BigDecimal(v.trim())));
    }

    /** La fila de ganancia válida de la lista, si hay (la primera). */
    static java.util.Optional<GananciaDefecto> ganancia(List<CargoDefecto> lista) {
        return lista.stream()
                .filter(d -> d != null && d.esGanancia() && d.value() != null && d.value().signum() >= 0)
                .filter(d -> "percent".equalsIgnoreCase(d.calcType()) || "fixed".equalsIgnoreCase(d.calcType()))
                .findFirst()
                .map(d -> new GananciaDefecto("percent".equalsIgnoreCase(d.calcType()) ? "percent" : "fixed", d.value()));
    }

    /** Los cargos por defecto configurados (vacío si no hay). */
    List<CargoDefecto> cargosPorDefecto(Long companyId) {
        String crudo = settings.findByCompanyIdAndSettingKey(companyId, CLAVE_CARGOS_DEFECTO)
                .map(c -> c.getSettingValue()).orElse(null);
        if (crudo == null || crudo.isBlank()) return List.of();
        try {
            List<CargoDefecto> lista = json.readValue(crudo, Argument.listOf(CargoDefecto.class));
            return lista == null ? List.of() : lista;
        } catch (IOException e) {
            throw new IllegalStateException("Los cargos por defecto de la configuración no se pueden leer; "
                    + "revísalos en Configuración › Cotizaciones.");
        }
    }

    /**
     * Los que se pueden copiar: con concepto activo, tipo válido y valor mayor
     * que cero (un porcentaje, hasta 100). Lo demás se salta en vez de crear
     * un cargo a medias.
     */
    static List<CargoDefecto> aplicables(List<CargoDefecto> lista, Set<Long> conceptosActivos) {
        List<CargoDefecto> out = new ArrayList<>();
        for (CargoDefecto d : lista) {
            if (d == null || d.esGanancia() || d.categoryId() == null || !conceptosActivos.contains(d.categoryId())) continue;
            if (d.value() == null || d.value().signum() <= 0) continue;
            boolean pct = "percent".equalsIgnoreCase(d.calcType());
            if (!pct && !"fixed".equalsIgnoreCase(d.calcType())) continue;
            if (pct && d.value().compareTo(new BigDecimal("100")) > 0) continue;
            out.add(d);
        }
        return out;
    }

    @Transactional
    public QuoteChargeDtos.Summary addCharge(Long quoteId, QuoteChargeDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        String calc = req.calcType() != null ? req.calcType().trim().toLowerCase() : "fixed";
        if (!calc.equals("fixed") && !calc.equals("percent")) {
            throw new IllegalStateException("El tipo de cargo debe ser 'fixed' o 'percent'.");
        }
        QuoteCharge c = new QuoteCharge();
        c.setCompanyId(companyId);
        c.setQuoteId(quoteId);
        c.setCategoryId(resolveCategory(req.categoryId(), companyId));
        c.setDescription(req.description());
        c.setCalcType(calc);
        c.setValue(req.value() != null ? req.value() : BigDecimal.ZERO);
        c.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        charges.save(c);
        return recompute(quoteId, companyId);
    }

    @Transactional
    public QuoteChargeDtos.Summary deleteCharge(Long quoteId, Long chargeId) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        charges.findByIdAndCompanyId(chargeId, companyId)
                .filter(c -> c.getQuoteId().equals(quoteId))
                .ifPresent(charges::delete);
        return recompute(quoteId, companyId);
    }

    /**
     * Cambia cómo se captura el gasto operativo de esta cotización.
     *
     * Pasar a monto único con varias partidas capturadas se rechaza en vez de
     * consolidarlas en silencio: el desglose es trabajo del usuario y fundirlo
     * sin avisar le borra información que no puede recuperar.
     */
    @Transactional
    public QuoteChargeDtos.Summary setOperatingMode(Long quoteId, String mode) {
        Long companyId = tenant.getCompanyId();
        var quote = quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        String modo = mode == null ? "" : mode.trim().toLowerCase();
        if (!modo.equals("single") && !modo.equals("detailed") && !modo.equals("percent")) {
            throw new IllegalStateException("El modo debe ser 'single', 'detailed' o 'percent'.");
        }
        if (modo.equals("single")) {
            long partidas = operatingCharges(companyId, quoteId).size();
            if (partidas > 1) {
                throw new IllegalStateException("Hay " + partidas + " partidas de gasto operativo capturadas. "
                        + "Déjalas en una sola o elimínalas antes de pasar a monto único.");
            }
        }
        quote.setOperatingExpenseMode(modo);
        quotes.update(quote);
        return recompute(quoteId, companyId);
    }

    /**
     * Fija el porcentaje de gasto operativo sobre el subtotal.
     *
     * Cambia también al modo 'percent': indicar un porcentaje y que la cifra
     * siga saliendo de las partidas capturadas sería un botón que no hace nada.
     */
    @Transactional
    public QuoteChargeDtos.Summary setOperatingPct(Long quoteId, BigDecimal pct) {
        Long companyId = tenant.getCompanyId();
        Quote quote = quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        BigDecimal valor = money(pct);
        if (valor.signum() < 0 || valor.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("El porcentaje de gasto operativo debe estar entre 0 y 100.");
        }
        quote.setOperatingExpensePct(valor);
        quote.setOperatingExpenseMode("percent");
        quotes.update(quote);
        return recompute(quoteId, companyId);
    }

    /**
     * Fija el gasto operativo como una sola cifra.
     *
     * Reutiliza la partida que ya exista en vez de acumular filas: en modo de
     * monto único el usuario espera estar corrigiendo el mismo número, no
     * agregando uno nuevo cada vez que guarda. Monto cero la borra.
     */
    @Transactional
    public QuoteChargeDtos.Summary setOperatingAmount(Long quoteId, QuoteChargeDtos.OperatingAmountRequest req) {
        Long companyId = tenant.getCompanyId();
        requireQuote(quoteId, companyId);
        List<QuoteCharge> existentes = operatingCharges(companyId, quoteId);
        BigDecimal monto = money(req.amount());

        if (monto.signum() <= 0) {
            existentes.forEach(charges::delete);
            return recompute(quoteId, companyId);
        }
        ChargeCategory operativa = categoriaOperativaPorDefecto(companyId);
        QuoteCharge fila = existentes.isEmpty() ? new QuoteCharge() : existentes.get(0);
        // Si había más de una, las sobrantes se van: el modo de monto único
        // solo admite una, y setOperatingMode ya impidió llegar aquí con varias.
        existentes.stream().skip(1).forEach(charges::delete);
        fila.setCompanyId(companyId);
        fila.setQuoteId(quoteId);
        fila.setCategoryId(operativa.getId());
        fila.setDescription(req.description() != null && !req.description().isBlank()
                ? req.description().trim() : operativa.getName());
        fila.setCalcType("fixed");
        fila.setValue(monto);
        fila.setSortOrder(fila.getSortOrder() != null ? fila.getSortOrder() : 5);
        charges.save(fila);
        return recompute(quoteId, companyId);
    }

    private List<QuoteCharge> operatingCharges(Long companyId, Long quoteId) {
        Map<Long, ChargeCategory> catalogo = categories.findByCompanyIdOrderBySortOrderAsc(companyId)
                .stream().collect(Collectors.toMap(ChargeCategory::getId, Function.identity()));
        return charges.findByCompanyIdAndQuoteIdOrderBySortOrderAsc(companyId, quoteId).stream()
                .filter(c -> esOperativo(c, catalogo))
                .toList();
    }

    /**
     * La categoría a la que se carga el monto único.
     *
     * Se prefiere OPERATIVO —la fija que siembra la migración 065— y si alguien
     * la renombró se toma la primera marcada como operativa. Sin ninguna no hay
     * dónde cargarlo, y decirlo es mejor que crear una a escondidas.
     */
    private ChargeCategory categoriaOperativaPorDefecto(Long companyId) {
        return categories.findByCompanyIdAndCode(companyId, "OPERATIVO")
                .or(() -> categories.findByCompanyIdOrderBySortOrderAsc(companyId).stream()
                        .filter(c -> Boolean.TRUE.equals(c.getOperating()))
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "No hay ningún concepto marcado como gasto operativo. "
                        + "Crea uno en Mantenimientos → Cotizaciones → Conceptos de gasto."));
    }

    /**
     * Fija el ajuste de cierre. Cero lo quita.
     *
     * No valida el estado de la cotización por la misma razón que addCharge
     * tampoco lo hace: el permiso de edición lo decide la pantalla, y meter
     * aquí una regla distinta dejaría dos criterios peleando.
     */
    @Transactional
    public QuoteChargeDtos.Summary setAdjustment(Long quoteId, BigDecimal amount) {
        Long companyId = tenant.getCompanyId();
        Quote quote = quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        quote.setManualAdjustment(money(amount));
        quotes.update(quote);
        return recompute(quoteId, companyId);
    }

    /** Catálogo activo, para el selector del cargo y el mantenimiento. */
    @Transactional
    public List<QuoteChargeDtos.CategoryResponse> listCategories() {
        return categories.findByCompanyIdAndStatusOrderBySortOrderAsc(tenant.getCompanyId(), "active")
                .stream()
                // El sort_order agrupa; el nombre desempata dentro del grupo.
                .sorted(Comparator.comparing(ChargeCategory::getSortOrder)
                        .thenComparing(ChargeCategory::getName, String.CASE_INSENSITIVE_ORDER))
                .map(QuoteChargeService::toCategoryResponse).toList();
    }

    @Transactional
    public QuoteChargeDtos.CategoryResponse createCategory(QuoteChargeDtos.CategoryRequest req) {
        Long companyId = tenant.getCompanyId();
        ChargeCategory c = new ChargeCategory();
        c.setCompanyId(companyId);
        c.setCode(codigoLibre(req.name(), companyId));
        aplicar(c, req);
        return toCategoryResponse(categories.save(c));
    }

    /**
     * El nombre y el orden se editan siempre. `operating` no se toca en una
     * categoría protegida: el bloque de Gastos Operativos se define por esa
     * bandera, y apagarla dejaría la cotización sin el renglón fijo.
     */
    @Transactional
    public QuoteChargeDtos.CategoryResponse updateCategory(Long id, QuoteChargeDtos.CategoryRequest req) {
        Long companyId = tenant.getCompanyId();
        ChargeCategory c = categories.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Concepto de gasto " + id + " no encontrado"));
        boolean protegida = Boolean.TRUE.equals(c.getProtectedRow());
        Boolean operatingAntes = c.getOperating();
        aplicar(c, req);
        if (protegida) {
            c.setOperating(operatingAntes);
            c.setStatus("active");
        }
        return toCategoryResponse(categories.update(c));
    }

    /**
     * Borrar solo lo que nadie usa. La FK de quote_charges ya lo impediría,
     * pero saldría un 500 con el mensaje crudo de Postgres en vez de decir
     * cuántas cotizaciones lo están usando.
     */
    @Transactional
    public void deleteCategory(Long id) {
        Long companyId = tenant.getCompanyId();
        ChargeCategory c = categories.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Concepto de gasto " + id + " no encontrado"));
        if (Boolean.TRUE.equals(c.getProtectedRow())) {
            throw new IllegalStateException("«" + c.getName() + "» es un concepto fijo del sistema y no se puede eliminar. "
                    + "Si no lo quieres ver en el selector, márcalo como inactivo.");
        }
        long enUso = charges.countByCompanyIdAndCategoryId(companyId, id);
        if (enUso > 0) {
            throw new IllegalStateException("«" + c.getName() + "» está en uso por " + enUso
                    + (enUso == 1 ? " cargo" : " cargos") + " y no se puede eliminar. Márcalo como inactivo.");
        }
        categories.delete(c);
    }

    private void aplicar(ChargeCategory c, QuoteChargeDtos.CategoryRequest req) {
        c.setName(req.name().trim());
        c.setOperating(Boolean.TRUE.equals(req.operating()));
        c.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 50);
        c.setStatus("inactive".equalsIgnoreCase(req.status()) ? "inactive" : "active");
    }

    /** Código derivado del nombre, con sufijo si ya existe uno igual. */
    private String codigoLibre(String nombre, Long companyId) {
        String base = nombre.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isBlank()) base = "GASTO";
        if (base.length() > 36) base = base.substring(0, 36);
        String candidato = base;
        int sufijo = 2;
        while (categories.findByCompanyIdAndCode(companyId, candidato).isPresent()) {
            candidato = base + "_" + sufijo++;
        }
        return candidato;
    }

    /**
     * Una categoría nula es válida: un cargo puede quedar sin clasificar, que
     * es como están los que vienen de antes del catálogo. Lo que no vale es
     * apuntar a una que no existe o que es de otra empresa.
     */
    private Long resolveCategory(Long categoryId, Long companyId) {
        if (categoryId == null) return null;
        return categories.findByIdAndCompanyId(categoryId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoría de gasto " + categoryId + " no encontrada"))
                .getId();
    }

    static QuoteChargeDtos.CategoryResponse toCategoryResponse(ChargeCategory c) {
        return new QuoteChargeDtos.CategoryResponse(c.getId(), c.getCode(), c.getName(),
                c.getOperating(), c.getProtectedRow(), c.getSortOrder(), c.getStatus());
    }

    /**
     * Recalcula el resumen y congela computed_amount en cada cargo.
     * Orden: materiales (derivado) + fixed = subtotal de costo; luego los
     * percent sobre ese subtotal; total = subtotal + percent.
     */
    /** Paso de redondeo del total (quotes.rounding_step): 0 = sin redondeo. */
    private BigDecimal pasoRedondeo(Long companyId) {
        return settings.findByCompanyIdAndSettingKey(companyId, "quotes.rounding_step")
                .map(c -> c.getSettingValue())
                .filter(v -> v != null && v.trim().matches("\\d{1,5}(\\.\\d{1,2})?"))
                .map(v -> new BigDecimal(v.trim()))
                .orElse(BigDecimal.ZERO);
    }

    /** down (hacia abajo, por defecto) · nearest (al más cercano) · up (hacia arriba). */
    private String modoRedondeo(Long companyId) {
        return settings.findByCompanyIdAndSettingKey(companyId, "quotes.rounding_mode")
                .map(c -> c.getSettingValue() == null ? "" : c.getSettingValue().trim().toLowerCase())
                .filter(v -> v.equals("nearest") || v.equals("up"))
                .orElse("down");
    }

    /** Redondea `valor` al múltiplo de `paso` según el modo. */
    static BigDecimal redondear(BigDecimal valor, BigDecimal paso, String modo) {
        RoundingMode rm = switch (modo) {
            case "up" -> RoundingMode.CEILING;
            case "nearest" -> RoundingMode.HALF_UP;
            default -> RoundingMode.FLOOR;
        };
        return valor.divide(paso, 0, rm).multiply(paso).setScale(2, RoundingMode.HALF_UP);
    }

    private String baseGanancia(Long companyId) {
        return settings.findByCompanyIdAndSettingKey(companyId, "quotes.profit_base")
                .map(c -> c.getSettingValue())
                .filter(v -> BASE_COSTO_DIRECTO.equalsIgnoreCase(v == null ? "" : v.trim()))
                .map(v -> BASE_COSTO_DIRECTO)
                .orElse(BASE_COSTO_TOTAL);
    }

    private QuoteChargeDtos.Summary recompute(Long quoteId, Long companyId) {
        var quote = quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        // SUBTOTAL = Σ(cantidad × precio_unitario). La cotización es un
        // documento de PRECIO: su total se construye desde lo que se le cobra
        // al cliente, no desde lo que cuesta. El costo sigue vivo en
        // materialsCost, que se informa aparte y alimenta el margen del
        // proyecto —ahí sí se compara precio contra costo.
        BigDecimal subtotal = money(quote.getItems().stream()
                .map(i -> i.getLineTotal() == null ? BigDecimal.ZERO : i.getLineTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal materialsCost = money(materials.findByCompanyIdAndQuoteId(companyId, quoteId).stream()
                .map(this::materialAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<QuoteCharge> rows = charges.findByCompanyIdAndQuoteIdOrderBySortOrderAsc(companyId, quoteId);
        Map<Long, ChargeCategory> catalogo = categories.findByCompanyIdOrderBySortOrderAsc(companyId)
                .stream().collect(Collectors.toMap(ChargeCategory::getId, Function.identity()));
        // Todo cargo se valúa contra el SUBTOTAL, sea fijo o porcentual.
        BigDecimal fixedTotal = BigDecimal.ZERO;
        BigDecimal percentTotal = BigDecimal.ZERO;
        for (QuoteCharge c : rows) {
            BigDecimal amt = "percent".equals(c.getCalcType())
                    ? money(subtotal.multiply(money(c.getValue())).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                    : money(c.getValue());
            c.setComputedAmount(amt);
            if ("percent".equals(c.getCalcType())) percentTotal = percentTotal.add(amt);
            else fixedTotal = fixedTotal.add(amt);
        }
        rows.forEach(charges::update);

        // Gasto operativo: en modo 'percent' sale del porcentaje configurado y
        // los cargos operativos capturados no cuentan —serían el mismo gasto
        // dos veces—; en 'single' y 'detailed' es la suma de esas partidas.
        boolean porPorcentaje = "percent".equalsIgnoreCase(quote.getOperatingExpenseMode());
        BigDecimal capturados = rows.stream()
                .filter(c -> esOperativo(c, catalogo))
                .map(c -> c.getComputedAmount() == null ? BigDecimal.ZERO : c.getComputedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal operatingExpenses = porPorcentaje
                ? money(subtotal.multiply(money(quote.getOperatingExpensePct()))
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                : capturados;

        // Los cargos que NO son operativos no caben en la fórmula del negocio,
        // pero desaparecerlos cambiaría el total en silencio: van en su propio
        // renglón, antes de la ganancia.
        BigDecimal otherCharges = money(fixedTotal.add(percentTotal).subtract(capturados));
        BigDecimal subtotalCost = money(subtotal.add(operatingExpenses));
        BigDecimal operatingCost = money(subtotalCost.add(otherCharges));
        String profitCalcType = "percent".equals(quote.getProfitCalcType()) ? "percent" : "fixed";
        BigDecimal profitValue = money(quote.getProfitValue());
        String profitBaseMode = baseGanancia(companyId);
        BigDecimal profitBase = BASE_COSTO_DIRECTO.equals(profitBaseMode) ? subtotal : operatingCost;
        BigDecimal profitAmount = "percent".equals(profitCalcType)
                ? money(profitBase.multiply(profitValue).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                : profitValue;
        BigDecimal taxableSubtotal = money(operatingCost.add(profitAmount));
        // El ajuste de cierre entra ANTES del IVA: el impuesto se calcula sobre
        // lo que de verdad se le cobra al cliente. Y la base no baja de cero
        // aunque el descuento sea mayor que el total, porque un IVA negativo no
        // existe y el error se propagaría a la factura.
        BigDecimal manualAdjustment = money(quote.getManualAdjustment());
        BigDecimal adjustedBase = money(taxableSubtotal.add(manualAdjustment)).max(BigDecimal.ZERO);
        BigDecimal taxRate = rateFor(quote);
        BigDecimal tax = money(adjustedBase.multiply(taxRate)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal total = money(adjustedBase.add(tax));

        // Redondeo automático del TOTAL (quotes.rounding_step / _mode): lo que
        // ve el cliente queda redondo (Q 8,995.50 → Q 8,995). La base y el IVA
        // se recalculan desde el total redondeado para que sigan cuadrando.
        BigDecimal roundingAdjustment = BigDecimal.ZERO;
        BigDecimal paso = pasoRedondeo(companyId);
        if (paso.signum() > 0 && total.signum() > 0) {
            BigDecimal redondo = redondear(total, paso, modoRedondeo(companyId));
            if (redondo.compareTo(total) != 0) {
                roundingAdjustment = money(redondo.subtract(total));
                BigDecimal divisor = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                adjustedBase = money(redondo.divide(divisor, 4, RoundingMode.HALF_UP));
                tax = money(redondo.subtract(adjustedBase));
                total = money(redondo);
            }
        }

        quote.setProfitCalcType(profitCalcType);
        quote.setProfitValue(profitValue);
        quote.setProfitAmount(profitAmount);
        quote.setTaxRate(taxRate);
        // El subtotal persistido es la base sobre la que se calculó el IVA:
        // con ajuste, esa es la ajustada, no la previa.
        quote.setSubtotal(adjustedBase);
        quote.setTax(tax);
        quote.setTotal(total);
        quotes.update(quote);

        List<QuoteChargeDtos.ChargeResponse> out = rows.stream()
                .map(c -> {
                    ChargeCategory cat = c.getCategoryId() == null ? null : catalogo.get(c.getCategoryId());
                    return new QuoteChargeDtos.ChargeResponse(c.getId(), c.getCategoryId(),
                            cat == null ? null : cat.getName(),
                            cat != null && Boolean.TRUE.equals(cat.getOperating()),
                            c.getDescription(), c.getCalcType(), c.getValue(),
                            c.getComputedAmount(), c.getSortOrder());
                })
                .toList();
        return new QuoteChargeDtos.Summary(money(materialsCost), money(fixedTotal),
                money(operatingExpenses), quote.getOperatingExpenseMode(),
                money(quote.getOperatingExpensePct()), money(subtotal), money(otherCharges),
                subtotalCost, money(percentTotal), operatingCost, profitCalcType, profitValue,
                profitAmount, taxableSubtotal, manualAdjustment, adjustedBase,
                taxRate, tax, total, out, money(profitBase), profitBaseMode, roundingAdjustment);
    }

    private boolean esOperativo(QuoteCharge c, Map<Long, ChargeCategory> catalogo) {
        if (c.getCategoryId() == null) return false;
        ChargeCategory cat = catalogo.get(c.getCategoryId());
        return cat != null && Boolean.TRUE.equals(cat.getOperating());
    }

    /**
     * La tasa que aplica a este documento.
     *
     * Un BORRADOR todavía se está armando: si la empresa cambia su IVA, sus
     * cotizaciones abiertas tienen que reflejarlo, y hasta ahora no lo hacían
     * —quedaban con la tasa del día en que se crearon aunque nadie las hubiera
     * enviado—. Desde que sale al cliente se congela: ese número ya lo vio
     * alguien y el documento no puede moverse solo.
     */
    public BigDecimal rateFor(Quote quote) {
        // Un prospecto del asistente también se está armando.
        if ("borrador".equalsIgnoreCase(quote.getStatus()) || "prospecto".equalsIgnoreCase(quote.getStatus())) {
            return taxService.rate();
        }
        return quote.getTaxRate() != null ? quote.getTaxRate() : taxService.rate();
    }

    private BigDecimal materialAmount(ProjectMaterial m) {
        BigDecimal unit = m.getUnitCostSnapshot() == null ? BigDecimal.ZERO : m.getUnitCostSnapshot();
        BigDecimal qty = m.getQuantityPlanned() == null ? BigDecimal.ZERO : m.getQuantityPlanned();
        return money(unit.multiply(qty));
    }

    private void requireQuote(Long quoteId, Long companyId) {
        quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
