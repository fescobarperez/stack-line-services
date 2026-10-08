package com.erp_maya.quote.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteChangeRequest;
import com.erp_maya.quote.domain.QuoteHistory;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.domain.QuoteNotification;
import com.erp_maya.quote.dto.ChangeRequestDtos;
import com.erp_maya.quote.dto.QuoteDtos;
import com.erp_maya.quote.repository.QuoteChangeRequestRepository;
import com.erp_maya.quote.repository.QuoteHistoryRepository;
import com.erp_maya.quote.repository.QuoteNotificationRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.settings.repository.CompanySettingRepository;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Solicitudes de cambio del cliente y su resolución por el vendedor.
 *
 * El asistente registra lo que el cliente pide sobre una cotización que ya
 * tomó un vendedor; no la modifica. El vendedor responde cada solicitud en el
 * panel y pulsa «Aplicar»: aquí se aplican juntas, se recalcula la cotización
 * (con {@link QuoteService#update}, la misma regla que el formulario), se deja
 * la bitácora y se encola el aviso al cliente. Todo en una transacción.
 */
@Singleton
public class QuoteChangeRequestService {

    public static final Set<String> TIPOS = Set.of("agregar", "quitar", "cantidad", "descuento", "condiciones", "otro", "consulta");
    private static final String CONSULTA = "consulta";
    private static final String MOTIVO_OTRO = "otro";
    private static final int MIN_COMENTARIO_OTRO = 15;
    private static final String AJUSTE_MOTIVOS = "quotes.change_reasons";

    /**
     * Catálogo por defecto. Cada empresa lo reemplaza con el ajuste
     * {@value #AJUSTE_MOTIVOS} (JSON con la misma forma).
     */
    public static final List<ChangeRequestDtos.Reason> MOTIVOS_DEFECTO = List.of(
            new ChangeRequestDtos.Reason("sin_existencia", "Sin existencia",
                    "No tenemos esa cantidad disponible por ahora."),
            new ChangeRequestDtos.Reason("politica_descuentos", "Política de descuentos",
                    "Ese descuento supera lo autorizado para este volumen de compra."),
            new ChangeRequestDtos.Reason("precio_minimo", "Precio mínimo",
                    "Ese precio está por debajo del mínimo que podemos ofrecer."),
            new ChangeRequestDtos.Reason("plazo_entrega", "Plazo de entrega",
                    "No podemos cumplir esa fecha de entrega."),
            new ChangeRequestDtos.Reason("producto_no_disponible", "Producto no disponible",
                    "Ese producto no lo manejamos o está descontinuado."),
            new ChangeRequestDtos.Reason(MOTIVO_OTRO, "Otro motivo", null));
    private static final Set<String> CIERRAN = Set.of("rechazada", "cancelada", "anulada", "vencida", "convertida", "abandonada");
    private static final String AVISO_CAMBIOS = "cambios_aplicados";

    private final QuoteRepository quotes;
    private final QuoteChangeRequestRepository solicitudes;
    private final QuoteHistoryRepository history;
    private final QuoteNotificationRepository avisos;
    private final ProductRepository products;
    private final QuoteService quoteService;
    private final TenantContext tenant;
    private final ObjectMapper json;
    private final CompanySettingRepository ajustes;

    public QuoteChangeRequestService(QuoteRepository quotes, QuoteChangeRequestRepository solicitudes,
                                     QuoteHistoryRepository history, QuoteNotificationRepository avisos,
                                     ProductRepository products, QuoteService quoteService,
                                     TenantContext tenant, ObjectMapper json, CompanySettingRepository ajustes) {
        this.ajustes = ajustes;
        this.quotes = quotes;
        this.solicitudes = solicitudes;
        this.history = history;
        this.avisos = avisos;
        this.products = products;
        this.quoteService = quoteService;
        this.tenant = tenant;
        this.json = json;
    }

    /** Motivos de rechazo/ajuste de la empresa (o los de por defecto). */
    @Transactional
    public List<ChangeRequestDtos.Reason> motivos() {
        return motivos(tenant.getCompanyId());
    }

    private List<ChangeRequestDtos.Reason> motivos(Long companyId) {
        return ajustes.findByCompanyIdAndSettingKey(companyId, AJUSTE_MOTIVOS)
                .map(s -> s.getSettingValue())
                .filter(v -> v != null && !v.isBlank())
                .map(v -> {
                    try {
                        List<ChangeRequestDtos.Reason> propios = json.readValue(v, Argument.listOf(ChangeRequestDtos.Reason.class));
                        // 'otro' siempre existe: sin él el vendedor no tendría salida.
                        boolean conOtro = propios.stream().anyMatch(r -> MOTIVO_OTRO.equals(r.code()));
                        if (conOtro) return propios;
                        var todos = new ArrayList<>(propios);
                        todos.add(MOTIVOS_DEFECTO.get(MOTIVOS_DEFECTO.size() - 1));
                        return (List<ChangeRequestDtos.Reason>) todos;
                    } catch (IOException e) {
                        return MOTIVOS_DEFECTO;
                    }
                })
                .orElse(MOTIVOS_DEFECTO);
    }

    @Transactional
    public List<ChangeRequestDtos.Response> list(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        cotizacion(quoteId, companyId);
        var catalogo = motivos(companyId);
        return solicitudes.findByCompanyIdAndQuoteIdOrderByCreatedAtAsc(companyId, quoteId)
                .stream().map(s -> toResponse(s, catalogo)).toList();
    }

    @Transactional
    public ChangeRequestDtos.Response create(Long quoteId, ChangeRequestDtos.CreateRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote q = cotizacion(quoteId, companyId);
        String tipo = req.kind().trim().toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) {
            throw new IllegalStateException("Tipo de solicitud inválido: " + req.kind() + ". Válidos: " + TIPOS);
        }
        if (CONSULTA.equals(tipo)) return consulta(q, companyId, req);
        if (QuoteService.ESTADO_ABIERTA.equalsIgnoreCase(q.getStatus())) {
            throw new IllegalStateException("La cotización está abierta: el asistente la modifica directamente");
        }
        if (CIERRAN.contains(q.getStatus() == null ? "" : q.getStatus().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("La cotización " + q.getDocNumber() + " está " + q.getStatus()
                    + " y ya no admite cambios");
        }
        boolean conProducto = Set.of("agregar", "quitar", "cantidad").contains(tipo);
        if (conProducto && req.productId() == null && (req.productName() == null || req.productName().isBlank())) {
            throw new IllegalStateException("Una solicitud de " + tipo + " necesita el producto");
        }
        if (("agregar".equals(tipo) || "cantidad".equals(tipo))
                && (req.quantity() == null || req.quantity().signum() <= 0)) {
            throw new IllegalStateException("Una solicitud de " + tipo + " necesita una cantidad mayor que cero");
        }

        QuoteChangeRequest s = new QuoteChangeRequest();
        s.setCompanyId(companyId);
        s.setQuoteId(quoteId);
        s.setKind(tipo);
        s.setProductId(req.productId());
        s.setProductName(nombreProducto(req.productId(), req.productName(), companyId));
        s.setQuantity(req.quantity());
        s.setDiscountPct(req.discountPct());
        s.setDetail(req.detail());
        s.setSource(req.source());
        s.setRequestedBy(req.requestedBy());
        QuoteChangeRequest guardada = solicitudes.save(s);
        history.save(new QuoteHistory(companyId, quoteId, "Solicitud de cambio del cliente: " + resumen(guardada),
                req.requestedBy()));
        return toResponse(guardada, motivos(companyId));
    }

    /**
     * El cliente pregunta por la respuesta a una solicitud (p. ej. por qué se
     * rechazó). Queda como solicitud 'consulta' ligada a la original; el
     * vendedor la contesta con «Responder» y le llega con el mismo aviso.
     */
    private ChangeRequestDtos.Response consulta(Quote q, Long companyId, ChangeRequestDtos.CreateRequest req) {
        if (req.parentId() == null) throw new IllegalStateException("Una consulta necesita la solicitud sobre la que pregunta");
        if (req.detail() == null || req.detail().isBlank()) throw new IllegalStateException("Falta la pregunta del cliente");
        QuoteChangeRequest padre = solicitudes.findById(req.parentId())
                .filter(p -> p.getCompanyId().equals(companyId) && p.getQuoteId().equals(q.getId()))
                .orElseThrow(() -> new IllegalStateException("La solicitud " + req.parentId() + " no es de esta cotización"));
        if (padre.pendiente()) {
            throw new IllegalStateException("Esa solicitud todavía no tiene respuesta: el asesor la está revisando");
        }
        QuoteChangeRequest s = new QuoteChangeRequest();
        s.setCompanyId(companyId);
        s.setQuoteId(q.getId());
        s.setKind(CONSULTA);
        s.setParentId(padre.getId());
        s.setDetail(req.detail().trim());
        s.setSource(req.source());
        s.setRequestedBy(req.requestedBy());
        QuoteChangeRequest guardada = solicitudes.save(s);
        history.save(new QuoteHistory(companyId, q.getId(),
                "Consulta del cliente sobre «" + resumen(padre) + "»: " + guardada.getDetail(), req.requestedBy()));
        return toResponse(guardada, motivos(companyId));
    }

    /** El vendedor resuelve las solicitudes que vio y pulsa «Aplicar». */
    @Transactional
    public ChangeRequestDtos.ApplyResult apply(Long quoteId, ChangeRequestDtos.ApplyRequest req, String actor) {
        Long companyId = tenant.getCompanyId();
        Quote q = quotes.findWithItems(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        if (QuoteService.ESTADO_PROSPECTO.equalsIgnoreCase(q.getStatus())
                || QuoteService.ESTADO_ABIERTA.equalsIgnoreCase(q.getStatus())) {
            throw new IllegalStateException("Abre la cotización para revisarla antes de aplicar cambios");
        }

        Map<Long, QuoteChangeRequest> porId = solicitudes
                .findByCompanyIdAndQuoteIdOrderByCreatedAtAsc(companyId, quoteId).stream()
                .collect(Collectors.toMap(QuoteChangeRequest::getId, Function.identity()));

        var catalogo = motivos(companyId);
        Map<String, ChangeRequestDtos.Reason> motivoPorCodigo = catalogo.stream()
                .collect(Collectors.toMap(ChangeRequestDtos.Reason::code, Function.identity(), (a, b) -> a));
        List<Linea> lineas = new ArrayList<>(q.getItems().stream().map(Linea::de).toList());
        boolean cambianLineas = false;
        Map<String, Integer> conteo = new HashMap<>();
        List<QuoteChangeRequest> resueltas = new ArrayList<>();
        Instant ahora = Instant.now();

        for (ChangeRequestDtos.Decision d : req.decisions()) {
            QuoteChangeRequest s = porId.get(d.id());
            if (s == null) throw new IllegalStateException("La solicitud " + d.id() + " no es de esta cotización");
            if (!s.pendiente()) throw new IllegalStateException("La solicitud " + d.id() + " ya fue resuelta");

            String decision = d.decision().trim().toLowerCase(Locale.ROOT);
            if (CONSULTA.equals(s.getKind()) && !"responder".equals(decision)) {
                throw new IllegalStateException("Una consulta solo se responde");
            }
            String estado = switch (decision) {
                case "rechazar" -> "rechazada";
                case "responder" -> "respondida";
                case "aceptar", "ajustar" -> {
                    boolean ajusta = "ajustar".equals(decision);
                    s.setAdjustedQuantity(ajusta ? d.adjustedQuantity() : null);
                    s.setAdjustedDiscountPct(ajusta ? d.adjustedDiscountPct() : null);
                    if (aplicar(s, lineas, companyId)) cambianLineas = true;
                    // condiciones/otro no cambian líneas: aceptarlas es responderlas.
                    boolean soloTexto = Set.of("condiciones", "otro").contains(s.getKind());
                    yield soloTexto ? "respondida" : ajusta ? "ajustada" : "aplicada";
                }
                default -> throw new IllegalStateException("Decisión inválida: " + d.decision()
                        + " (aceptar, ajustar, rechazar o responder)");
            };
            String comentario = d.response() == null ? "" : d.response().trim();
            if ("rechazada".equals(estado) || "ajustada".equals(estado)) {
                // El cliente va a leer por qué. Motivo del catálogo obligatorio;
                // con 'otro' (sin texto propio) el comentario es la explicación.
                ChangeRequestDtos.Reason motivo = d.reasonCode() == null ? null : motivoPorCodigo.get(d.reasonCode());
                if (motivo == null) {
                    throw new IllegalStateException("Elige el motivo del " + ("rechazada".equals(estado) ? "rechazo" : "ajuste")
                            + " de: " + resumen(s));
                }
                if (motivo.clientText() == null && comentario.length() < MIN_COMENTARIO_OTRO) {
                    throw new IllegalStateException("Explica al cliente el motivo (al menos " + MIN_COMENTARIO_OTRO
                            + " caracteres) de: " + resumen(s));
                }
                s.setReasonCode(motivo.code());
            } else if ("respondida".equals(estado) && comentario.isEmpty()) {
                throw new IllegalStateException("Escribe la respuesta para el cliente de: " + resumen(s));
            }
            s.setStatus(estado);
            s.setResponse(comentario.isEmpty() ? null : comentario);
            s.setResolvedBy(actor);
            s.setResolvedAt(ahora);
            resueltas.add(s);
            conteo.merge(estado, 1, Integer::sum);
        }

        boolean estabaEnviada = "enviada".equalsIgnoreCase(q.getStatus());
        QuoteDtos.Response actualizada;
        if (cambianLineas) {
            if (lineas.isEmpty()) throw new IllegalStateException("La cotización debe conservar al menos una línea");
            if (estabaEnviada) {
                // Lo enviado no se edita: vuelve a borrador y el vendedor la
                // reenvía como versión nueva. Así el cliente aprueba lo que ve.
                q.setStatus(QuoteService.ESTADO_BORRADOR);
                quotes.update(q);
                history.save(new QuoteHistory(companyId, quoteId,
                        "Vuelve a borrador para preparar una nueva versión con los cambios del cliente", actor));
            }
            actualizada = quoteService.update(quoteId, new QuoteDtos.UpdateRequest(
                    q.getValidUntil(), q.getNotes(), q.getProfitCalcType(), q.getProfitValue(),
                    lineas.stream().map(Linea::request).toList()));
        } else {
            actualizada = quoteService.get(quoteId);
        }
        resueltas.forEach(solicitudes::update);

        String bitacora = "Solicitudes resueltas: " + conteo.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.joining(", "));
        history.save(new QuoteHistory(companyId, quoteId, bitacora, actor));

        boolean avisa = q.getConversationRef() != null && !q.getConversationRef().isBlank();
        if (avisa) encolarAviso(q, actualizada, resueltas, catalogo, porId,
                estabaEnviada && !cambianLineas, estabaEnviada && cambianLineas);

        List<ChangeRequestDtos.Response> todas = solicitudes
                .findByCompanyIdAndQuoteIdOrderByCreatedAtAsc(companyId, quoteId).stream()
                .map(s -> toResponse(s, catalogo)).toList();
        return new ChangeRequestDtos.ApplyResult(actualizada, todas, avisa);
    }

    // ── internos ────────────────────────────────────────────────────────────

    /** Aplica una solicitud aceptada a las líneas en memoria. Devuelve si cambió alguna. */
    private boolean aplicar(QuoteChangeRequest s, List<Linea> lineas, Long companyId) {
        BigDecimal cantidad = s.getAdjustedQuantity() != null ? s.getAdjustedQuantity() : s.getQuantity();
        BigDecimal pct = s.getAdjustedDiscountPct() != null ? s.getAdjustedDiscountPct() : s.getDiscountPct();
        Linea existente = s.getProductId() == null ? null
                : lineas.stream().filter(l -> s.getProductId().equals(l.productId)).findFirst().orElse(null);

        switch (s.getKind()) {
            case "agregar" -> {
                positiva(cantidad, s);
                if (existente != null) {
                    existente.quantity = existente.quantity.add(cantidad);
                } else {
                    if (s.getProductId() == null) {
                        throw new IllegalStateException("Para agregar «" + s.getProductName()
                                + "» elige el producto del catálogo o agrégalo a mano en la cotización");
                    }
                    Product p = products.findByIdAndCompanyId(s.getProductId(), companyId)
                            .orElseThrow(() -> new ResourceNotFoundException("Producto " + s.getProductId() + " no encontrado"));
                    lineas.add(new Linea(null, p.getId(), p.getName(), cantidad, p.getPrice(), BigDecimal.ZERO, p.getUnit()));
                }
                return true;
            }
            case "quitar" -> {
                if (existente == null) throw new IllegalStateException("«" + s.getProductName() + "» no está en la cotización");
                lineas.remove(existente);
                return true;
            }
            case "cantidad" -> {
                if (existente == null) throw new IllegalStateException("«" + s.getProductName() + "» no está en la cotización");
                positiva(cantidad, s);
                existente.quantity = cantidad;
                return true;
            }
            case "descuento" -> {
                if (pct == null || pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
                    throw new IllegalStateException("Indica un descuento entre 0 y 100 % para: " + resumen(s));
                }
                if (existente != null) existente.discount = pct;
                else lineas.forEach(l -> l.discount = pct);
                return true;
            }
            default -> { return false; }
        }
    }

    private static void positiva(BigDecimal cantidad, QuoteChangeRequest s) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new IllegalStateException("Indica una cantidad mayor que cero para: " + resumen(s));
        }
    }

    private void encolarAviso(Quote q, QuoteDtos.Response actualizada, List<QuoteChangeRequest> resueltas,
                              List<ChangeRequestDtos.Reason> catalogo, Map<Long, QuoteChangeRequest> porId,
                              boolean pedirDecision, boolean nuevaVersion) {
        var resultados = resueltas.stream().map(s -> {
            // Una consulta se presenta con la solicitud por la que pregunta.
            QuoteChangeRequest padre = s.getParentId() != null ? porId.get(s.getParentId()) : null;
            String detalle = padre != null ? "Sobre «" + resumen(padre) + "»" : resumenFinal(s);
            return new ChangeRequestDtos.NotificationItem(
                    s.getId(), s.getKind(), s.getStatus(), detalle, resumen(s), mensajeCliente(s, catalogo));
        }).toList();
        var payload = new ChangeRequestDtos.ChangesAppliedPayload(
                q.getId(), q.getDocNumber(), q.getClientName(), dinero(actualizada.total()),
                // Si viene una versión nueva, no se manda el PDF de la que quedó a medias.
                nuevaVersion ? null : "/api/quotes/" + q.getId() + "/pdf", resultados,
                pedirDecision, nuevaVersion, q.getSentVersion());
        QuoteNotification n = new QuoteNotification();
        n.setCompanyId(q.getCompanyId());
        n.setQuoteId(q.getId());
        n.setKind(AVISO_CAMBIOS);
        n.setChannel(q.getChannel());
        n.setConversationRef(q.getConversationRef());
        try {
            n.setPayload(json.writeValueAsString(payload));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo armar el aviso al cliente", e);
        }
        avisos.save(n);
    }

    private Quote cotizacion(Long id, Long companyId) {
        return quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
    }

    private String nombreProducto(Long productId, String nombre, Long companyId) {
        if (productId == null) return nombre;
        return products.findByIdAndCompanyId(productId, companyId).map(Product::getName).orElse(nombre);
    }

    /** Lo que pidió el cliente, en una línea. */
    static String resumen(QuoteChangeRequest s) {
        return texto(s, s.getQuantity(), s.getDiscountPct());
    }

    /** Lo que quedó, con el ajuste del vendedor si lo hubo. */
    static String resumenFinal(QuoteChangeRequest s) {
        return texto(s,
                s.getAdjustedQuantity() != null ? s.getAdjustedQuantity() : s.getQuantity(),
                s.getAdjustedDiscountPct() != null ? s.getAdjustedDiscountPct() : s.getDiscountPct());
    }

    private static String texto(QuoteChangeRequest s, BigDecimal cantidad, BigDecimal pct) {
        String producto = s.getProductName() != null ? s.getProductName() : "producto";
        return switch (s.getKind()) {
            case "agregar" -> "Agregar " + num(cantidad) + " × " + producto;
            case "quitar" -> "Quitar " + producto;
            case "cantidad" -> "Cambiar " + producto + " a " + num(cantidad);
            case "descuento" -> "Descuento de " + num(pct) + " %" + (s.getProductName() != null ? " en " + producto : "");
            case CONSULTA -> "Consulta: " + (s.getDetail() != null ? s.getDetail() : "");
            default -> s.getDetail() != null ? s.getDetail() : s.getKind();
        };
    }

    private static String num(BigDecimal v) {
        return v == null ? "?" : v.stripTrailingZeros().toPlainString();
    }

    private static String dinero(BigDecimal v) {
        return "Q " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US))
                .format((v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP));
    }

    /** Lo que lee el cliente: la explicación del motivo y, si hay, el comentario del vendedor. */
    static String mensajeCliente(QuoteChangeRequest s, List<ChangeRequestDtos.Reason> catalogo) {
        String explicacion = s.getReasonCode() == null ? null : catalogo.stream()
                .filter(r -> r.code().equals(s.getReasonCode())).map(ChangeRequestDtos.Reason::clientText)
                .filter(t -> t != null && !t.isBlank()).findFirst().orElse(null);
        String comentario = s.getResponse();
        if (explicacion == null) return comentario;
        if (comentario == null || comentario.isBlank()) return explicacion;
        return explicacion + " " + comentario;
    }

    private ChangeRequestDtos.Response toResponse(QuoteChangeRequest s, List<ChangeRequestDtos.Reason> catalogo) {
        String etiqueta = s.getReasonCode() == null ? null : catalogo.stream()
                .filter(r -> r.code().equals(s.getReasonCode())).map(ChangeRequestDtos.Reason::label)
                .findFirst().orElse(s.getReasonCode());
        return new ChangeRequestDtos.Response(s.getId(), s.getKind(), s.getProductId(), s.getProductName(),
                s.getQuantity(), s.getDiscountPct(), s.getDetail(), s.getStatus(), s.getResponse(),
                s.getAdjustedQuantity(), s.getAdjustedDiscountPct(), s.getSource(), s.getRequestedBy(),
                s.getResolvedBy(), s.getResolvedAt(), s.getCreatedAt(), resumen(s),
                s.getReasonCode(), etiqueta, mensajeCliente(s, catalogo), s.getParentId());
    }

    /** Una línea de la cotización mientras se aplican los cambios. */
    private static final class Linea {
        final Long id;
        final Long productId;
        final String description;
        BigDecimal quantity;
        final BigDecimal unitPrice;
        BigDecimal discount;
        final String uom;

        Linea(Long id, Long productId, String description, BigDecimal quantity,
              BigDecimal unitPrice, BigDecimal discount, String uom) {
            this.id = id;
            this.productId = productId;
            this.description = description;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.discount = discount;
            this.uom = uom;
        }

        static Linea de(QuoteItem i) {
            String nombre = i.getItemName() != null && !i.getItemName().isBlank() ? i.getItemName()
                    : i.getProduct() != null ? i.getProduct().getName() : i.getDescription();
            return new Linea(i.getId(), i.getProduct() != null ? i.getProduct().getId() : null, nombre,
                    i.getQuantity(), i.getUnitPrice(), i.getDiscount(), i.getUom());
        }

        QuoteDtos.UpdateItemRequest request() {
            return new QuoteDtos.UpdateItemRequest(id, description, quantity, unitPrice, discount, productId, uom);
        }
    }
}
