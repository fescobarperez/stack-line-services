package com.erp_maya.template.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.domain.ProductSupplier;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.catalog.repository.ProductSupplierRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.template.domain.OptionChoice;
import com.erp_maya.template.domain.ProductTemplate;
import com.erp_maya.template.domain.TemplateComponent;
import com.erp_maya.template.domain.TemplateOption;
import com.erp_maya.template.domain.TemplateParam;
import com.erp_maya.template.domain.TemplatePreset;
import com.erp_maya.template.dto.TemplateDtos;
import com.erp_maya.template.repository.TemplateRepositories.Choices;
import com.erp_maya.template.repository.TemplateRepositories.Components;
import com.erp_maya.template.repository.TemplateRepositories.Options;
import com.erp_maya.template.repository.TemplateRepositories.Params;
import com.erp_maya.template.repository.TemplateRepositories.Presets;
import com.erp_maya.template.repository.TemplateRepositories.Templates;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Plantillas de cálculo: alta, edición y cálculo. Una plantilla describe
 * solo la materia prima del producto; la mano de obra y los demás costos se
 * agregan en la cotización.
 *
 * Una plantilla se guarda completa y cada guardado sube la versión. Antes de
 * guardar se valida todo lo que después haría fallar un cálculo: fórmulas que
 * usan parámetros que no existen, componentes que no son materia prima,
 * combinaciones que apuntan a elecciones inexistentes.
 */
@Singleton
public class TemplateService {

    static final Pattern CODIGO = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");
    static final String MATERIA_PRIMA = "raw_material";
    static final String VENDIBLE = "sellable";

    private final Templates templates;
    private final Params params;
    private final Options options;
    private final Choices choices;
    private final Components components;
    private final Presets presets;
    private final ProductRepository products;
    private final ProductSupplierRepository productSuppliers;
    private final TenantContext tenant;
    private final ObjectMapper json;

    public TemplateService(Templates templates, Params params, Options options, Choices choices,
                           Components components, Presets presets,
                           ProductRepository products, ProductSupplierRepository productSuppliers,
                           TenantContext tenant, ObjectMapper json) {
        this.templates = templates;
        this.params = params;
        this.options = options;
        this.choices = choices;
        this.components = components;
        this.presets = presets;
        this.products = products;
        this.productSuppliers = productSuppliers;
        this.tenant = tenant;
        this.json = json;
    }

    // ── consulta ──────────────────────────────────────────────────────

    @Transactional
    public List<TemplateDtos.Summary> list() {
        Long companyId = tenant.getCompanyId();
        return templates.findByCompanyIdOrderByNameAsc(companyId).stream().map(t -> new TemplateDtos.Summary(
                t.getId(), t.getProductId(), nombreProducto(t.getProductId(), companyId), t.getName(),
                Boolean.TRUE.equals(t.getActive()), t.getVersion(),
                params.findByTemplateIdOrderBySortOrderAsc(t.getId()).size(),
                options.findByTemplateIdOrderBySortOrderAsc(t.getId()).size(),
                components.findByTemplateIdOrderBySortOrderAsc(t.getId()).size())).toList();
    }

    @Transactional
    public TemplateDtos.Response get(Long id) {
        return toResponse(buscar(id));
    }

    /** La plantilla activa del producto, si tiene (la usa el asistente). */
    @Transactional
    public Optional<TemplateDtos.Response> forProduct(Long productId) {
        return templates.findByCompanyIdAndProductId(tenant.getCompanyId(), productId)
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .map(this::toResponse);
    }

    // ── guardar ───────────────────────────────────────────────────────

    @Transactional
    public TemplateDtos.Response save(Long id, TemplateDtos.Save req) {
        Long companyId = tenant.getCompanyId();
        Product producto = products.findByIdAndCompanyId(req.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + req.productId() + " no encontrado"));
        if (!VENDIBLE.equalsIgnoreCase(producto.getItemType())) {
            throw new IllegalStateException("La plantilla es de un producto que se vende; «" + producto.getName()
                    + "» es materia prima o servicio");
        }
        validar(req, companyId);

        ProductTemplate t;
        if (id == null) {
            templates.findByCompanyIdAndProductId(companyId, req.productId()).ifPresent(x -> {
                throw new IllegalStateException("«" + producto.getName() + "» ya tiene plantilla; edítala");
            });
            t = new ProductTemplate();
            t.setCompanyId(companyId);
            t.setVersion(1);
        } else {
            t = buscar(id);
            if (!t.getProductId().equals(req.productId())) {
                templates.findByCompanyIdAndProductId(companyId, req.productId()).ifPresent(x -> {
                    throw new IllegalStateException("«" + producto.getName() + "» ya tiene plantilla");
                });
            }
            t.setVersion(t.getVersion() + 1);
            // Las partes se reemplazan completas. Las líneas de cotización ya
            // calculadas guardan su desglose en project_materials: no cambian.
            components.deleteByTemplateId(t.getId());
            presets.deleteByTemplateId(t.getId());
            options.deleteByTemplateId(t.getId()); // las elecciones caen en cascada
            params.deleteByTemplateId(t.getId());
        }
        t.setProductId(req.productId());
        t.setName(req.name().trim());
        t.setDescription(vacio(req.description()));
        t.setActive(req.active() == null || req.active());
        ProductTemplate guardada = id == null ? templates.save(t) : templates.update(t);
        Long tid = guardada.getId();

        int orden = 0;
        for (TemplateDtos.Param p : req.params()) {
            TemplateParam e = new TemplateParam();
            e.setCompanyId(companyId);
            e.setTemplateId(tid);
            e.setCode(p.code().trim());
            e.setLabel(p.label().trim());
            e.setUnit(vacio(p.unit()));
            e.setMinValue(p.min());
            e.setMaxValue(p.max());
            e.setDefaultValue(p.defaultValue());
            e.setRequired(p.required() == null || p.required());
            e.setSortOrder(orden++);
            params.save(e);
        }

        // opción → (elección → id), para enlazar componentes y cargos.
        Map<String, Map<String, Long>> ids = new HashMap<>();
        orden = 0;
        for (TemplateDtos.Option o : lista(req.options())) {
            TemplateOption e = new TemplateOption();
            e.setCompanyId(companyId);
            e.setTemplateId(tid);
            e.setCode(o.code().trim());
            e.setLabel(o.label().trim());
            e.setRequired(o.required() == null || o.required());
            e.setSortOrder(orden++);
            TemplateOption op = options.save(e);
            Map<String, Long> porCodigo = new HashMap<>();
            int oc = 0;
            for (TemplateDtos.Choice c : o.choices()) {
                OptionChoice ch = new OptionChoice();
                ch.setCompanyId(companyId);
                ch.setOptionId(op.getId());
                ch.setCode(c.code().trim());
                ch.setLabel(c.label().trim());
                ch.setRenderText(vacio(c.renderText()));
                ch.setBenefit(vacio(c.benefit()));
                ch.setIsDefault(Boolean.TRUE.equals(c.isDefault()));
                ch.setSortOrder(oc++);
                porCodigo.put(ch.getCode(), choices.save(ch).getId());
            }
            ids.put(op.getCode(), porCodigo);
        }

        orden = 0;
        for (TemplateDtos.Component c : req.components()) {
            TemplateComponent e = new TemplateComponent();
            e.setCompanyId(companyId);
            e.setTemplateId(tid);
            e.setChoiceId(eleccion(ids, c.option(), c.choice()));
            e.setProductId(c.productId());
            e.setQuantityFormula(c.formula().trim());
            e.setWastePct(c.wastePct() == null ? BigDecimal.ZERO : c.wastePct());
            e.setRoundUp(Boolean.TRUE.equals(c.roundUp()));
            e.setGroupName(c.group() == null || c.group().isBlank() ? "Materiales" : c.group().trim());
            e.setSortOrder(orden++);
            components.save(e);
        }

        orden = 0;
        for (TemplateDtos.Preset p : lista(req.presets())) {
            TemplatePreset e = new TemplatePreset();
            e.setCompanyId(companyId);
            e.setTemplateId(tid);
            e.setCode(p.code().trim());
            e.setLabel(p.label().trim());
            e.setChoicesJson(escribir(p.choices()));
            e.setSortOrder(orden++);
            presets.save(e);
        }
        return toResponse(guardada);
    }

    /** Apagar en vez de borrar: las cotizaciones ya calculadas la referencian. */
    @Transactional
    public TemplateDtos.Response setActive(Long id, boolean active) {
        ProductTemplate t = buscar(id);
        t.setActive(active);
        return toResponse(templates.update(t));
    }

    /** Todo lo que haría fallar un cálculo, con un mensaje que dice qué corregir. */
    void validar(TemplateDtos.Save req, Long companyId) {
        if (req.params().isEmpty()) throw new IllegalStateException("La plantilla necesita al menos un parámetro (p. ej. ancho)");
        if (req.components().isEmpty()) throw new IllegalStateException("La plantilla necesita al menos un componente");
        Set<String> codigos = new LinkedHashSet<>();
        for (TemplateDtos.Param p : req.params()) {
            String c = codigo(p.code(), "parámetro");
            if (!codigos.add(c)) throw new IllegalStateException("El parámetro '" + c + "' está repetido");
            if (p.min() != null && p.max() != null && p.min().compareTo(p.max()) > 0) {
                throw new IllegalStateException(p.label() + ": el mínimo es mayor que el máximo");
            }
            if (p.defaultValue() != null && ((p.min() != null && p.defaultValue().compareTo(p.min()) < 0)
                    || (p.max() != null && p.defaultValue().compareTo(p.max()) > 0))) {
                throw new IllegalStateException(p.label() + ": el valor por defecto está fuera del rango");
            }
        }
        Map<String, Set<String>> elecciones = new HashMap<>();
        for (TemplateDtos.Option o : lista(req.options())) {
            String c = codigo(o.code(), "opción");
            if (codigos.contains(c) || elecciones.containsKey(c)) throw new IllegalStateException("El código '" + c + "' está repetido");
            if (o.choices().isEmpty()) throw new IllegalStateException(o.label() + " no tiene elecciones");
            Set<String> chs = new HashSet<>();
            long defectos = 0;
            for (TemplateDtos.Choice ch : o.choices()) {
                if (!chs.add(codigo(ch.code(), "elección"))) throw new IllegalStateException(o.label() + ": la elección '" + ch.code() + "' está repetida");
                if (Boolean.TRUE.equals(ch.isDefault())) defectos++;
            }
            if (defectos > 1) throw new IllegalStateException(o.label() + " tiene más de una elección por defecto");
            elecciones.put(c, chs);
        }
        for (TemplateDtos.Component c : req.components()) {
            Product mp = products.findByIdAndCompanyId(c.productId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Materia prima " + c.productId() + " no encontrada"));
            if (!MATERIA_PRIMA.equalsIgnoreCase(mp.getItemType())) {
                throw new IllegalStateException("«" + mp.getName() + "» no es materia prima; la mano de obra y otros costos se agregan en la cotización");
            }
            Formula.validar(c.formula(), codigos);
            if (c.wastePct() != null && (c.wastePct().signum() < 0 || c.wastePct().compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new IllegalStateException("La merma de «" + mp.getName() + "» va de 0 a 100 %");
            }
            refEleccion(elecciones, c.option(), c.choice());
        }
        Set<String> presetCodigos = new HashSet<>();
        for (TemplateDtos.Preset p : lista(req.presets())) {
            if (!presetCodigos.add(codigo(p.code(), "combinación"))) throw new IllegalStateException("La combinación '" + p.code() + "' está repetida");
            p.choices().forEach((op, ch) -> refEleccion(elecciones, op, ch));
        }
    }

    // ── cálculo ───────────────────────────────────────────────────────

    @Transactional
    public TemplateDtos.Calculation calculate(Long id, TemplateDtos.CalculateRequest req) {
        ProductTemplate t = buscar(id);
        return toCalculation(calcular(t, req.params(), req.choices(), req.preset()));
    }

    /** El cálculo con los costos vigentes; lo usa también la cotización. */
    @Transactional
    public TemplateCalculator.Result calcular(ProductTemplate t, Map<String, BigDecimal> pedidos,
                                              Map<String, String> elegidas, String preset) {
        Long companyId = t.getCompanyId();
        if (!Boolean.TRUE.equals(t.getActive())) throw new IllegalStateException("La plantilla «" + t.getName() + "» está apagada");
        Map<String, String> elecciones = new LinkedHashMap<>();
        if (preset != null && !preset.isBlank()) {
            TemplatePreset p = presets.findByTemplateIdOrderBySortOrderAsc(t.getId()).stream()
                    .filter(x -> x.getCode().equals(preset.trim())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("La plantilla no tiene la combinación '" + preset + "'"));
            elecciones.putAll(leer(p.getChoicesJson()));
        }
        if (elegidas != null) elecciones.putAll(elegidas);
        Map<Long, TemplateCalculator.Cost> cache = new HashMap<>();
        Function<Long, TemplateCalculator.Cost> costos = pid -> cache.computeIfAbsent(pid, x -> costo(x, companyId));
        return TemplateCalculator.calcular(definicion(t), pedidos == null ? Map.of() : pedidos, elecciones, costos);
    }

    @Transactional
    public ProductTemplate buscar(Long id) {
        return templates.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla " + id + " no encontrada"));
    }

    /** Proveedor preferido (o el primero) y su costo; sin proveedor, el costo del producto. */
    private TemplateCalculator.Cost costo(Long productId, Long companyId) {
        Product p = products.findByIdAndCompanyId(productId, companyId).orElse(null);
        if (p == null) return null;
        List<ProductSupplier> provs = productSuppliers.findByCompanyIdAndProductIdOrderByPreferredDesc(companyId, productId);
        if (!provs.isEmpty()) {
            ProductSupplier ps = provs.get(0);
            return new TemplateCalculator.Cost(p.getName(), unidad(p), ps.getSupplierId(), ps.getUnitCost());
        }
        return new TemplateCalculator.Cost(p.getName(), unidad(p), null, p.getCost());
    }

    TemplateCalculator.Definition definicion(ProductTemplate t) {
        List<TemplateParam> ps = params.findByTemplateIdOrderBySortOrderAsc(t.getId());
        List<TemplateOption> os = options.findByTemplateIdOrderBySortOrderAsc(t.getId());
        Map<Long, List<OptionChoice>> porOpcion = os.isEmpty() ? Map.of()
                : choices.findByOptionIdInOrderBySortOrderAsc(os.stream().map(TemplateOption::getId).toList())
                .stream().collect(Collectors.groupingBy(OptionChoice::getOptionId));
        return new TemplateCalculator.Definition(
                nombreProducto(t.getProductId(), t.getCompanyId()),
                ps.stream().map(p -> new TemplateCalculator.Param(p.getCode(), p.getLabel(), p.getUnit(), p.getMinValue(),
                        p.getMaxValue(), p.getDefaultValue(), Boolean.TRUE.equals(p.getRequired()))).toList(),
                os.stream().map(o -> new TemplateCalculator.Option(o.getCode(), o.getLabel(), Boolean.TRUE.equals(o.getRequired()),
                        porOpcion.getOrDefault(o.getId(), List.of()).stream().map(c -> new TemplateCalculator.Choice(
                                c.getId(), c.getCode(), c.getLabel(), c.getRenderText(), c.getBenefit(),
                                Boolean.TRUE.equals(c.getIsDefault()))).toList())).toList(),
                components.findByTemplateIdOrderBySortOrderAsc(t.getId()).stream().map(c -> new TemplateCalculator.Component(
                        c.getId(), c.getChoiceId(), c.getProductId(), c.getQuantityFormula(), c.getWastePct(),
                        Boolean.TRUE.equals(c.getRoundUp()), c.getGroupName())).toList());
    }

    // ── respuestas ────────────────────────────────────────────────────

    TemplateDtos.Response toResponse(ProductTemplate t) {
        Long companyId = t.getCompanyId();
        List<TemplateOption> os = options.findByTemplateIdOrderBySortOrderAsc(t.getId());
        Map<Long, List<OptionChoice>> porOpcion = os.isEmpty() ? Map.of()
                : choices.findByOptionIdInOrderBySortOrderAsc(os.stream().map(TemplateOption::getId).toList())
                .stream().collect(Collectors.groupingBy(OptionChoice::getOptionId));
        // elección id → (código de opción, código de elección)
        Map<Long, String[]> refs = new HashMap<>();
        List<TemplateDtos.Option> opciones = new ArrayList<>();
        for (TemplateOption o : os) {
            List<OptionChoice> cs = porOpcion.getOrDefault(o.getId(), List.of());
            cs.forEach(c -> refs.put(c.getId(), new String[]{o.getCode(), c.getCode()}));
            opciones.add(new TemplateDtos.Option(o.getCode(), o.getLabel(), o.getRequired(),
                    cs.stream().map(c -> new TemplateDtos.Choice(c.getCode(), c.getLabel(), c.getRenderText(),
                            c.getBenefit(), c.getIsDefault())).toList()));
        }
        List<TemplateDtos.Component> comps = components.findByTemplateIdOrderBySortOrderAsc(t.getId()).stream().map(c -> {
            String[] r = c.getChoiceId() == null ? null : refs.get(c.getChoiceId());
            Product p = products.findByIdAndCompanyId(c.getProductId(), companyId).orElse(null);
            return new TemplateDtos.Component(c.getProductId(), p == null ? null : p.getName(), p == null ? null : unidad(p),
                    r == null ? null : r[0], r == null ? null : r[1], c.getQuantityFormula(), c.getWastePct(),
                    c.getRoundUp(), c.getGroupName());
        }).toList();
        return new TemplateDtos.Response(t.getId(), t.getProductId(), nombreProducto(t.getProductId(), companyId),
                t.getName(), t.getDescription(), Boolean.TRUE.equals(t.getActive()), t.getVersion(),
                params.findByTemplateIdOrderBySortOrderAsc(t.getId()).stream().map(p -> new TemplateDtos.Param(
                        p.getCode(), p.getLabel(), p.getUnit(), p.getMinValue(), p.getMaxValue(), p.getDefaultValue(),
                        p.getRequired())).toList(),
                opciones, comps,
                presets.findByTemplateIdOrderBySortOrderAsc(t.getId()).stream().map(p -> new TemplateDtos.Preset(
                        p.getCode(), p.getLabel(), leer(p.getChoicesJson()))).toList(),
                t.getUpdatedAt());
    }

    static TemplateDtos.Calculation toCalculation(TemplateCalculator.Result r) {
        return new TemplateDtos.Calculation(r.params(), r.choices(),
                r.lines().stream().map(l -> new TemplateDtos.Line(l.productId(), l.productName(), l.unit(), l.group(),
                        l.quantity(), l.supplierId(), l.unitCost(), l.amount(), l.missingCost())).toList(),
                r.materialsCost(), r.description(), r.renderText(), r.missingCosts());
    }

    // ── utilidades ────────────────────────────────────────────────────

    private String nombreProducto(Long productId, Long companyId) {
        return products.findByIdAndCompanyId(productId, companyId).map(Product::getName).orElse("Producto " + productId);
    }

    private static String unidad(Product p) {
        return p.getUnit() == null || p.getUnit().isBlank() ? "unid" : p.getUnit();
    }

    private static Long eleccion(Map<String, Map<String, Long>> ids, String opcion, String eleccion) {
        if (opcion == null || opcion.isBlank()) return null;
        return ids.get(opcion.trim()).get(eleccion.trim());
    }

    private static void refEleccion(Map<String, Set<String>> elecciones, String opcion, String eleccion) {
        boolean sinOpcion = opcion == null || opcion.isBlank();
        boolean sinEleccion = eleccion == null || eleccion.isBlank();
        if (sinOpcion && sinEleccion) return;
        if (sinOpcion || sinEleccion) throw new IllegalStateException("Indica opción y elección juntas (o ninguna, si va siempre)");
        Set<String> chs = elecciones.get(opcion.trim());
        if (chs == null) throw new IllegalStateException("No existe la opción '" + opcion + "'");
        if (!chs.contains(eleccion.trim())) throw new IllegalStateException("La opción '" + opcion + "' no tiene la elección '" + eleccion + "'");
    }

    private static String codigo(String c, String que) {
        String v = c == null ? "" : c.trim();
        if (!CODIGO.matcher(v).matches()) {
            throw new IllegalStateException("Código de " + que + " inválido '" + v + "': minúsculas, números y _, empezando con letra");
        }
        return v;
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> List<T> lista(List<T> l) {
        return l == null ? List.of() : l;
    }

    String escribir(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Map<String, String> leer(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            return json.readValue(s, Argument.mapOf(String.class, String.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
