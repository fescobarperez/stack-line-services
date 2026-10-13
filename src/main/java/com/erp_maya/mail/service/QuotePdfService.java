package com.erp_maya.mail.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.quote.service.QuoteChargeService;
import com.erp_maya.quote.service.QuoteConditionService;
import com.erp_maya.quote.service.QuotePlanService;
import com.erp_maya.quote.dto.QuotePlanDtos;
import com.erp_maya.quote.domain.QuoteCondition;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.settings.repository.CompanySettingRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Genera el PDF de la cotización en el servidor, con el MISMO diseño que el
 * del navegador (maya-ui/src/lib/quotePdf.js): franja de marca, encabezado con
 * logo y datos de la empresa, tarjeta del cliente, líneas, cargos, notas,
 * totales y pie.
 *
 * Lo usan el correo, `GET /api/quotes/{id}/pdf` y WhatsApp: se genera sin que
 * nadie tenga una pantalla abierta, por eso no puede depender del navegador.
 *
 * Lo que sale de la configuración de la empresa (company_settings):
 *   company.logo_url, brand.primary_color, brand.secondary_color,
 *   company.nit, company.address, company.phone, company.email
 * El nombre y el NIT de respaldo vienen de la tabla companies.
 *
 * openhtmltopdf no resuelve flexbox, grid, gradientes, object-fit ni rgba: la
 * maquetación es de tablas, los tintes se precalculan en hex y el logo se
 * descarga, se mide y se incrusta como data URI ya escalado.
 */
@Singleton
public class QuotePdfService {

    private static final Logger log = LoggerFactory.getLogger(QuotePdfService.class);

    // Paleta base del front (PALETTE en quotePdf.js).
    private static final String INK = "#25242a";
    private static final String MUTED = "#726a72";
    private static final String BORDER = "#e8dfe3";
    private static final String PRIMARIO = "#8b1e3f";
    private static final String PRIMARIO_SUAVE = "#f8e9ee";
    private static final String SECUNDARIO = "#d47a5a";
    private static final String SECUNDARIO_SUAVE = "#fdf0eb";

    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-GT"));
    private static final int LOGO_PX = 48;
    private static final Duration LOGO_VIGENCIA = Duration.ofMinutes(10);

    private final CompanyRepository companies;
    private final CompanySettingRepository settings;
    private final TenantContext tenant;
    private final QuoteRepository quotes;
    private final QuoteChargeService cargos;
    private final Materials materiales;
    private final ProductRepository productos;
    private final QuoteConditionService condicionesEmpresa;
    private final QuotePlanService planes;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** El logo se baja una vez y se reutiliza: el correo y WhatsApp lo piden seguido. */
    private final Map<String, LogoEnCache> logos = new ConcurrentHashMap<>();

    public QuotePdfService(CompanyRepository companies, CompanySettingRepository settings,
                           TenantContext tenant, QuoteRepository quotes, QuoteChargeService cargos,
                           Materials materiales, ProductRepository productos,
                           QuoteConditionService condicionesEmpresa, QuotePlanService planes) {
        this.planes = planes;
        this.condicionesEmpresa = condicionesEmpresa;
        this.materiales = materiales;
        this.productos = productos;
        this.companies = companies;
        this.settings = settings;
        this.tenant = tenant;
        this.quotes = quotes;
        this.cargos = cargos;
    }

    /** El PDF de una cotización de la empresa actual, o vacío si no existe. */
    public Optional<byte[]> generar(Long quoteId) {
        // Con fetch join: recorrer las líneas de una entidad desprendida revienta.
        return quotes.findWithItems(quoteId, tenant.getCompanyId()).map(this::generar);
    }

    public byte[] generar(Quote q) {
        Long companyId = tenant.getCompanyId();
        Empresa empresa = empresa(companyId);
        QuoteChargeDtos.Summary resumen = resumen(q);

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(xhtml(q, empresa, resumen, materiaPrima(q, companyId), condiciones(companyId),
                    planDePago(q)), null);
            b.toStream(salida);
            b.run();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de la cotización: " + e.getMessage(), e);
        }
    }

    // ── Datos ─────────────────────────────────────────────────────────────

    /** Lo que la empresa configuró: nombre, NIT, contacto, logo y colores. */
    record Empresa(String nombre, String nit, String direccion, String telefono, String correo,
                   String logoUrl, String primario, String secundario, String lema,
                   /** Bloque de firma al final; null = no se muestra. */
                   Firma firma) {}

    /** Línea de firma con el nombre y la leyenda (Configuración → Cotizaciones). */
    record Firma(String nombre, String leyenda) {}

    private Empresa empresa(Long companyId) {
        Company c = companies.findById(companyId).orElse(null);
        String nombre = c != null && c.getName() != null ? c.getName() : "";
        String nitTabla = c != null && c.getNit() != null ? c.getNit() : "";
        return new Empresa(
                nombre,
                ajuste(companyId, "company.nit", nitTabla),
                ajuste(companyId, "company.address", ""),
                ajuste(companyId, "company.phone", ""),
                ajuste(companyId, "company.email", ""),
                ajuste(companyId, "company.logo_url", ""),
                color(ajuste(companyId, "brand.primary_color", ""), PRIMARIO),
                color(ajuste(companyId, "brand.secondary_color", ""), SECUNDARIO),
                ajuste(companyId, "company.tagline", ""),
                "true".equalsIgnoreCase(ajuste(companyId, "quotes.signature.enabled", "false"))
                        ? new Firma(ajuste(companyId, "quotes.signature.name", nombre),
                                    ajuste(companyId, "quotes.signature.label", "Firma del fabricante"))
                        : null);
    }

    private String ajuste(Long companyId, String clave, String porDefecto) {
        return settings.findByCompanyIdAndSettingKey(companyId, clave)
                .map(s -> s.getSettingValue())
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .orElse(porDefecto);
    }

    /** Cargos y totales tal como los calcula el ERP; sin ellos sale con los de la cotización. */
    private QuoteChargeDtos.Summary resumen(Quote q) {
        try {
            return cargos.getSummary(q.getId());
        } catch (RuntimeException e) {
            log.warn("PDF de {} sin cargos: {}", q.getDocNumber(), e.getMessage());
            return null;
        }
    }

    // ── Documento ─────────────────────────────────────────────────────────

    /**
     * Materia prima de cada línea: los materiales del proyecto asignados a esa
     * línea (project_materials.quote_item_id). Solo los nombres, sin repetir.
     */
    private Map<Long, List<String>> materiaPrima(Quote q, Long companyId) {
        try {
            Map<Long, String> nombres = new java.util.HashMap<>();
            Map<Long, List<String>> porLinea = new java.util.LinkedHashMap<>();
            for (ProjectMaterial m : materiales.findByCompanyIdAndQuoteId(companyId, q.getId())) {
                if (m.getQuoteItemId() == null || "cancelled".equalsIgnoreCase(m.getStatus())) continue;
                String nombre = nombres.computeIfAbsent(m.getProductId(), id -> productos.findByIdAndCompanyId(id, companyId)
                        .map(p -> p.getName()).orElse(null));
                if (nombre == null) continue;
                List<String> lista = porLinea.computeIfAbsent(m.getQuoteItemId(), k -> new ArrayList<>());
                if (!lista.contains(nombre)) lista.add(nombre);
            }
            return porLinea;
        } catch (RuntimeException ex) {
            log.warn("PDF de {} sin materia prima: {}", q.getDocNumber(), ex.getMessage());
            return Map.of();
        }
    }

    String xhtml(Quote q, Empresa e, QuoteChargeDtos.Summary r, Map<Long, List<String>> materiaPrima,
                 List<QuoteCondition> condiciones, List<QuotePlanDtos.TermResponse> plan) {
        String p = e.primario();
        String s = e.secundario();
        String pSuave = e.primario().equals(PRIMARIO) ? PRIMARIO_SUAVE : tinte(p, 0.1);
        String sSuave = e.secundario().equals(SECUNDARIO) ? SECUNDARIO_SUAVE : tinte(s, 0.1);

        BigDecimal subtotal = r != null && r.adjustedBase() != null ? r.adjustedBase() : q.getSubtotal();
        BigDecimal iva = r != null && r.tax() != null ? r.tax() : q.getTax();
        BigDecimal total = r != null && r.total() != null ? r.total() : q.getTotal();
        BigDecimal tasa = q.getTaxRate() != null ? q.getTaxRate() : (r != null ? r.taxRate() : null);

        boolean preliminar = esPreliminar(q);
        // Precio global: el cliente ve el alcance y el total, no cómo se armó.
        boolean global = "global".equalsIgnoreCase(q.getPriceDisplay());
        String notas = vacio(q.getNotes(), "Propuesta preparada según el alcance acordado con el cliente.");

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="es">
            <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
            <title>%s · %s</title>
            <style>
              @page { size: A4; margin: 13mm 14mm; }
              body { margin: 0; font-family: Helvetica, Arial, sans-serif; font-size: 11px;
                     line-height: 1.4; color: %s; }
              table { width: 100%%; border-collapse: collapse; font-size: 11px; }
              td { vertical-align: top; padding: 0; }
              .ink { color: %s; }
              .muted { color: %s; }
              .franja td { height: 7px; font-size: 1px; line-height: 1px; }
              .cabecera { margin-top: 25px; border-bottom: 1px solid %s; }
              .cabecera td.col { padding-bottom: 20px; }
              .logo { width: 48px; height: 48px; background: #0a090c; border-radius: 9px;
                      text-align: center; vertical-align: middle; }
              .logo-vacio { width: 72px; height: 42px; border: 1.5px dashed %s; border-radius: 7px;
                            text-align: center; vertical-align: middle; color: %s;
                            font-size: 10px; font-weight: bold; letter-spacing: 1px; }
              .empresa { font-size: 17px; font-weight: bold; color: %s; margin: 2px 0; }
              .empresa-sub { font-size: 9px; color: %s; line-height: 1.5; }
              .lema { font-size: 10px; font-style: italic; margin-bottom: 3px; }
              .tipo { font-size: 23px; line-height: 1; font-weight: bold; color: %s; }
              .numero { font-family: Courier, monospace; font-weight: bold; font-size: 11px;
                        color: %s; margin-top: 7px; }
              .fecha { font-size: 9px; color: %s; margin-top: 3px; }
              .estado { display: inline-block; margin-top: 9px; padding: 5px 8px; border-radius: 9px;
                        background: %s; color: %s; font-size: 8px; font-weight: bold;
                        letter-spacing: 0.6px; }
              .cliente { margin: 22px 0 24px; background: %s; border-left: 4px solid %s; }
              .cliente td { padding: 14px 16px; }
              .ceja { font-size: 8px; font-weight: bold; letter-spacing: 1px; color: %s; margin-bottom: 4px; }
              .cliente-nombre { font-size: 14px; font-weight: bold; color: %s; }
              .cliente-meta { font-size: 9px; color: %s; margin-top: 3px; }
              .seccion td { font-size: 9px; font-weight: bold; letter-spacing: 1.2px; color: %s;
                            padding-bottom: 9px; white-space: nowrap; }
              .seccion td.linea { width: 100%%; padding-left: 8px; vertical-align: middle; }
              .seccion .raya { border-top: 1px solid %s; height: 1px; font-size: 1px; }
              .items th { padding: 9px 7px; background: %s; color: %s; font-size: 8px;
                          letter-spacing: 0.6px; text-align: left; }
              .items th.c, .items td.c { width: 46px; text-align: center; }
              .items th.r, .items td.r { width: 94px; text-align: right; }
              .items td { padding: 11px 7px; border-bottom: 1px solid %s; }
              .items .desc { font-size: 10px; font-weight: bold; color: %s; }
              .items .det { font-size: 8px; color: %s; margin-top: 3px; }
              .items .fuerte { font-weight: bold; }
              .items td.incluido { font-size: 9px; font-weight: bold; letter-spacing: 0.5px; color: %s; }
              .items tr.sub td { padding: 2px 7px 4px 22px; border-bottom: 0; font-size: 8.5px; color: #726a72; }
              .items tr.cargo td { background: %s; }
              .items tr.cargo td.primera { border-left: 3px solid %s; }
              .items tr { page-break-inside: avoid; }
              .vacio { text-align: center; color: %s; padding: 22px; }
              .resumen { margin-top: 22px; page-break-inside: avoid; }
              .notas { padding: 13px 14px; background: %s; border-top: 3px solid %s; border-radius: 8px;
                       font-size: 9px; color: %s; min-height: 82px; }
              .notas-titulo { color: %s; font-size: 9px; font-weight: bold; letter-spacing: 1px;
                              margin-bottom: 5px; }
              .totales td { padding: 5px 0; font-size: 10px; color: %s; }
              .totales td.v { text-align: right; color: %s; font-weight: bold; }
              .final td { background: %s; color: #ffffff; font-size: 11px; font-weight: bold;
                          padding: 12px 13px; vertical-align: middle; }
              .final td.v { text-align: right; font-size: 16px; }
              .pie { margin-top: 34px; border-top: 1px solid %s; font-size: 8px; color: %s;
                     page-break-inside: avoid; }
              .pie td { padding-top: 12px; }
              .legal { margin-top: 18px; color: %s; font-size: 8px; line-height: 1.45; text-align: center; }
              .plan { page-break-inside: avoid; }
              .plan th { padding: 6px 7px; font-size: 8px; letter-spacing: 0.6px; color: #726a72; text-align: left;
                         border-bottom: 1px solid #e8dfe3; }
              .plan th.r, .plan td.r { text-align: right; }
              .plan td { padding: 6px 7px; font-size: 9.5px; color: #25242a; border-bottom: 1px solid #e8dfe3; }
              .plan td.fuerte { font-weight: bold; }
              .condiciones { page-break-inside: avoid; }
              .firma { margin-top: 48px; page-break-inside: avoid; }
              .firma td { text-align: center; }
              .firma-linea { width: 240px; margin: 0 auto 6px; border-top: 1px solid #25242a; height: 1px; font-size: 1px; }
              .firma-nombre { font-size: 10px; font-weight: bold; color: #25242a; letter-spacing: 0.5px; }
              .firma-leyenda { font-size: 9px; font-style: italic; color: #726a72; margin-top: 2px; }
              .condiciones td { padding: 4px 0; font-size: 9.5px; color: #25242a; }
              .condiciones td.cond-titulo { width: 130px; padding-right: 12px; font-weight: bold; color: #726a72; }
              .preliminar { margin: 0 0 18px; border: 1px solid #e0b84c; background: #fff8e1; }
              .preliminar td { padding: 8px 10px; font-size: 9px; color: #5c4400; }
            </style></head>
            <body>
              <table class="franja"><tr>
                <td style="width:70%%; background:%s">&#160;</td>
                <td style="width:30%%; background:%s">&#160;</td>
              </tr></table>

              <table class="cabecera"><tr>
                <td class="col">
                  <table style="width:auto"><tr>
                    <td style="padding-right:12px">%s</td>
                    <td>
                      <div class="empresa">%s</div>
                      %s
                      <div class="empresa-sub">%s</div>
                    </td>
                  </tr></table>
                </td>
                <td class="col" style="width:170px; text-align:right">
                  <div class="tipo">%s</div>
                  <div class="numero">%s</div>
                  <div class="fecha">Emitida el %s</div>
                  <div class="estado">%s</div>
                </td>
              </tr></table>

              %s

              <table class="cliente"><tr>
                <td style="width:65%%">
                  <div class="ceja">PREPARADO PARA</div>
                  <div class="cliente-nombre">%s</div>
                  <div class="cliente-meta">%s</div>
                  %s
                </td>
                <td style="text-align:right">
                  <div class="ceja">VIGENCIA</div>
                  <div class="ink" style="font-size:10px; font-weight:bold">%s</div>
                  <div class="cliente-meta">%s</div>
                </td>
              </tr></table>

              <table class="seccion"><tr>
                <td>RESUMEN DE PROPUESTA</td>
                <td class="linea"><div class="raya">&#160;</div></td>
              </tr></table>
              <table class="items">
                <thead><tr>%s</tr></thead>
                <tbody>%s</tbody>
              </table>

              <table class="resumen"><tr>
                <td style="padding-right:28px">
                  <div class="notas">
                    <div class="notas-titulo">NOTAS</div>
                    %s<br /><br />Este documento es una propuesta comercial y no constituye una factura fiscal.
                  </div>
                </td>
                <td style="width:210px">
                  <table class="totales">
                    <tr><td>Subtotal</td><td class="v">%s</td></tr>
                    <tr><td>IVA (%s%%)</td><td class="v">%s</td></tr>
                  </table>
                  <table class="final" style="margin-top:7px"><tr>
                    <td style="border-radius:7px 0 0 7px">Total</td>
                    <td class="v" style="border-radius:0 7px 7px 0">%s</td>
                  </tr></table>
                </td>
              </tr></table>

              %s

              %s

              %s

              <table class="pie"><tr>
                <td><b style="color:%s">%s</b><br />Gracias por confiar en nosotros</td>
                <td style="text-align:right">Documento generado desde Stackline<br />Válido según las condiciones indicadas</td>
              </tr></table>
              <div class="legal">Los precios y condiciones de esta cotización corresponden al alcance descrito.
                Cualquier modificación deberá ser aprobada por ambas partes.</div>
            </body></html>
            """.formatted(
                // título
                esc(q.getDocNumber()), esc(vacio(e.nombre(), "Cotización")),
                // estilos
                INK, INK, MUTED, BORDER,
                p, p,                               // logo vacío
                INK, MUTED,                         // empresa
                p, INK, MUTED,                      // tipo, número, fecha
                pSuave, p,                          // estado
                pSuave, p, p, INK, MUTED,           // cliente
                p, BORDER,                          // sección
                pSuave, p, BORDER, INK, MUTED,      // items
                e.primario(),                       // incluido
                sSuave, s,                          // cargos
                MUTED,                              // vacío
                sSuave, s, MUTED, INK,              // notas
                MUTED, INK,                         // totales
                p,                                  // total final
                BORDER, MUTED, MUTED,               // pie, legal
                // franja
                p, s,
                // cabecera
                logo(e),
                esc(vacio(e.nombre(), "Nombre de empresa")),
                e.lema().isBlank() ? "" : "<div class=\"lema\" style=\"color:" + p + "\">" + esc(e.lema()) + "</div>",
                datosEmpresa(e),
                preliminar ? "COTIZACIÓN<br />PRELIMINAR" : "COTIZACIÓN",
                esc(vacio(q.getDocNumber(), "—")),
                fechaLarga(q.getQuoteDate() == null ? LocalDate.now() : q.getQuoteDate()),
                esc(vacio(q.getStatus(), "Vigente").toUpperCase(Locale.ROOT)),
                // aviso preliminar
                preliminar ? AVISO_PRELIMINAR : "",
                // cliente
                esc(vacio(q.getClientName(), "Cliente sin nombre")),
                datosCliente(q),
                q.getDeliveryAddress() == null || q.getDeliveryAddress().isBlank() ? ""
                        : "<div class=\"cliente-meta\" style=\"margin-top:6px\"><b class=\"ink\">Entrega / instalación:</b> "
                          + esc(q.getDeliveryAddress()) + "</div>",
                vigenciaPrincipal(q), vigenciaDetalle(q),
                // líneas
                global ? "<th>DESCRIPCIÓN</th><th class=\"c\">CANT.</th><th class=\"r\">&#160;</th>"
                       : "<th>DESCRIPCIÓN</th><th class=\"c\">CANT.</th><th class=\"r\">PRECIO UNITARIO</th><th class=\"r\">TOTAL</th>",
                filas(q, r, global, materiaPrima),
                // notas
                br(notas),
                // totales
                dinero(subtotal), num(tasa), dinero(iva), dinero(total),
                // plan de pago y condiciones comerciales
                planDePagoHtml(plan, total),
                condicionesComerciales(q, condiciones),
                // firma
                firma(e.firma()),
                // pie
                p, esc(vacio(e.nombre(), "")));
    }

    /**
     * «Condiciones comerciales»: forma de pago y tiempo de entrega de la
     * cotización, luego las condiciones activas de la empresa (mantenimiento
     * en Configuración), en su orden. Sin ninguna, la sección no aparece.
     */
    static String condicionesComerciales(Quote q, List<QuoteCondition> condiciones) {
        List<String[]> filas = new ArrayList<>();
        if (q.getPaymentTerms() != null && !q.getPaymentTerms().isBlank()) filas.add(new String[]{"Forma de pago", q.getPaymentTerms()});
        if (q.getLeadTime() != null && !q.getLeadTime().isBlank()) filas.add(new String[]{"Tiempo de entrega", q.getLeadTime()});
        for (QuoteCondition c : condiciones) filas.add(new String[]{c.getTitle(), c.getBody()});
        if (filas.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(
                "<table class=\"seccion\" style=\"margin-top:26px\"><tr><td>CONDICIONES COMERCIALES</td>"
                + "<td class=\"linea\"><div class=\"raya\">&#160;</div></td></tr></table>"
                + "<table class=\"condiciones\">");
        for (String[] f : filas) {
            sb.append("<tr><td class=\"cond-titulo\">").append(esc(f[0])).append("</td><td>")
              .append(br(f[1])).append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    /** Línea de firma centrada, como en la cotización en papel. */
    static String firma(Firma f) {
        if (f == null) return "";
        return "<table class=\"firma\"><tr><td>"
                + "<div class=\"firma-linea\">&#160;</div>"
                + "<div class=\"firma-nombre\">" + esc(f.nombre()) + "</div>"
                + (f.leyenda().isBlank() ? "" : "<div class=\"firma-leyenda\">" + esc(f.leyenda()) + "</div>")
                + "</td></tr></table>";
    }

    /** Cuotas del plan de pagos de la cotización (vacío si no tiene o falla). */
    private List<QuotePlanDtos.TermResponse> planDePago(Quote q) {
        try {
            return planes.getPlan(q.getId()).terms();
        } catch (RuntimeException ex) {
            log.warn("PDF de {} sin plan de pago: {}", q.getDocNumber(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * «Plan de pago»: una fila por cuota con su concepto, vencimiento, % del
     * total y monto (como «Anticipo 50 % · Saldo contra entrega» en papel).
     */
    static String planDePagoHtml(List<QuotePlanDtos.TermResponse> plan, BigDecimal total) {
        if (plan == null || plan.isEmpty()) return "";
        BigDecimal base = total == null || total.signum() == 0 ? null : total;
        StringBuilder sb = new StringBuilder(
                "<table class=\"seccion\" style=\"margin-top:26px\"><tr><td>PLAN DE PAGO</td>"
                + "<td class=\"linea\"><div class=\"raya\">&#160;</div></td></tr></table>"
                + "<table class=\"plan\"><tr><th>Concepto</th><th>Vence</th><th class=\"r\">%</th><th class=\"r\">Monto</th></tr>");
        for (int k = 0; k < plan.size(); k++) {
            QuotePlanDtos.TermResponse c = plan.get(k);
            String concepto = c.notes() != null && !c.notes().isBlank() ? c.notes()
                    : plan.size() > 1 && k == 0 ? "Anticipo"
                    : plan.size() > 1 && k == plan.size() - 1 ? "Saldo"
                    : "Pago " + (k + 1);
            String vence = c.dueDate() == null ? "—" : fechaLarga(c.dueDate());
            String pct = base == null || c.amount() == null ? "—"
                    : c.amount().multiply(new BigDecimal("100")).divide(base, 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
            sb.append("<tr><td>").append(esc(concepto)).append("</td><td>").append(vence).append("</td><td class=\"r\">")
              .append(pct).append("</td><td class=\"r fuerte\">").append(dinero(c.amount())).append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private List<QuoteCondition> condiciones(Long companyId) {
        try {
            return condicionesEmpresa.activas(companyId);
        } catch (RuntimeException ex) {
            log.warn("PDF sin condiciones comerciales: {}", ex.getMessage());
            return List.of();
        }
    }

    private static final String AVISO_PRELIMINAR = """
            <table class="preliminar"><tr><td>
              <b>Cotización preliminar.</b> Fue preparada por nuestro asistente y aún debe ser
              revisada y aprobada por un agente de ventas. Precios, existencias y condiciones
              pueden cambiar hasta su confirmación.
            </td></tr></table>
            """;

    /** abierta / prospecto: la armó el asistente y nadie revisó sus precios. */
    private static boolean esPreliminar(Quote q) {
        return "prospecto".equalsIgnoreCase(q.getStatus()) || "abierta".equalsIgnoreCase(q.getStatus());
    }

    private static String datosEmpresa(Empresa e) {
        List<String> partes = new ArrayList<>();
        if (!e.nit().isBlank()) partes.add("NIT " + esc(e.nit()));
        if (!e.direccion().isBlank()) partes.add(esc(e.direccion()));
        List<String> contacto = new ArrayList<>();
        if (!e.telefono().isBlank()) contacto.add("Tel. " + esc(e.telefono()));
        if (!e.correo().isBlank()) contacto.add(esc(e.correo()));
        if (!contacto.isEmpty()) partes.add(String.join(" · ", contacto));
        return String.join("<br />", partes);
    }

    private static String datosCliente(Quote q) {
        List<String> partes = new ArrayList<>();
        if (q.getClientContact() != null && !q.getClientContact().isBlank()) partes.add(esc(q.getClientContact()));
        if (q.getClientEmail() != null && !q.getClientEmail().isBlank()) partes.add(esc(q.getClientEmail()));
        if (q.getClientNit() != null && !q.getClientNit().isBlank()) partes.add("NIT " + esc(q.getClientNit()));
        return String.join(" · ", partes);
    }

    /**
     * Productos, luego los cargos uno por uno y los gastos operativos en una
     * sola línea: su desglose es estructura de costo interna (igual que el front).
     */
    static String filas(Quote q, QuoteChargeDtos.Summary r, boolean global, Map<Long, List<String>> materiaPrima) {
        int columnas = global ? 3 : 4;
        Map<Long, BigDecimal> totales = totalesAlCliente(q, r);
        StringBuilder sb = new StringBuilder();
        for (QuoteItem i : q.getItems()) {
            if (global && Boolean.FALSE.equals(i.getShowToClient())) continue;
            BigDecimal cantidad = cero(i.getQuantity());
            BigDecimal descuento = cero(i.getDiscount());
            // Lo que paga el cliente por la línea: su precio (el especial que
            // puso el asesor o el del catálogo) más su parte de la ganancia,
            // el ajuste y el redondeo, para que las líneas sumen el subtotal.
            BigDecimal totalLinea = totales.getOrDefault(i.getId(), cero(i.getLineTotal()));
            BigDecimal unitario = cantidad.signum() > 0
                    ? totalLinea.divide(cantidad, 2, RoundingMode.HALF_UP) : totalLinea;
            List<String> det = new ArrayList<>();
            if (i.getUom() != null && !i.getUom().isBlank()) det.add(esc(i.getUom()));
            if (descuento.signum() > 0) det.add("Descuento " + num(descuento) + "%");
            // La descripción solo si agrega algo (no repetir el nombre).
            if (i.getDescription() != null && !i.getDescription().isBlank()
                    && !i.getDescription().trim().equalsIgnoreCase(textoNombre(i))) det.add(esc(i.getDescription()));
            String medidas = medidas(i);
            if (!medidas.isEmpty()) det.add(medidas);
            if (global) {
                sb.append(filaIncluida(nombre(i), String.join(" · ", det), num(cantidad)));
            } else {
                sb.append(fila(nombre(i), String.join(" · ", det), num(cantidad), unitario, totalLinea, false));
            }
            // Desglose de materia prima: líneas secundarias, sin cantidad ni precio.
            for (String material : materiaPrima.getOrDefault(i.getId(), List.of())) {
                sb.append("<tr class=\"sub\"><td colspan=\"").append(columnas).append("\">&#8226; ")
                  .append(esc(material)).append("</td></tr>");
            }
        }
        // Con precio global, cargos y gastos son parte del costo: no se listan.
        if (!global && r != null && r.charges() != null) {
            for (QuoteChargeDtos.ChargeResponse c : r.charges()) {
                if (Boolean.TRUE.equals(c.operating())) continue;
                BigDecimal monto = cero(c.computedAmount());
                sb.append(fila(esc(vacio(c.description(), "Cargo adicional")),
                        esc(vacio(c.category(), "Cargo de la propuesta")), "1", monto, monto, true));
            }
            BigDecimal operativos = cero(r.operatingExpenses());
            if (operativos.signum() > 0) {
                sb.append(fila("Gastos operativos", "", "1", operativos, operativos, true));
            }
        }
        return sb.isEmpty()
                ? "<tr><td colspan=\"4\" class=\"vacio\">No hay líneas en esta cotización.</td></tr>"
                : sb.toString();
    }

    /**
     * "2.00 × 1.50 × 0.60 m", más "medida especial" si difiere del catálogo.
     * La medida del catálogo no se muestra: es dato interno del vendedor.
     */
    static String medidas(QuoteItem i) {
        List<String> partes = new ArrayList<>();
        for (BigDecimal v : new BigDecimal[]{i.getHeight(), i.getWidth(), i.getDepth()}) {
            if (v != null) partes.add(v.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        if (partes.isEmpty()) return "";
        String unidad = i.getProduct() != null && i.getProduct().getDimensionUnit() != null
                ? i.getProduct().getDimensionUnit() : "m";
        boolean especial = (i.getBaseHeight() != null || i.getBaseWidth() != null || i.getBaseDepth() != null)
                && (difiere(i.getHeight(), i.getBaseHeight()) || difiere(i.getWidth(), i.getBaseWidth())
                    || difiere(i.getDepth(), i.getBaseDepth()));
        String estado = "review".equals(i.getSizePricing()) ? " · precio por confirmar" : "";
        return esc(String.join(" × ", partes) + " " + unidad) + (especial ? " · medida especial" : "") + estado;
    }

    private static boolean difiere(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a != b : a.compareTo(b) != 0;
    }

    /** Línea de alcance: sin precio, «Incluido». */
    private static String filaIncluida(String descripcion, String detalle, String cantidad) {
        return "<tr><td class=\"primera\"><div class=\"desc\">" + descripcion + "</div>"
                + (detalle.isBlank() ? "" : "<div class=\"det\">" + detalle + "</div>") + "</td>"
                + "<td class=\"c\">" + cantidad + "</td>"
                + "<td class=\"r incluido\">Incluido</td>"
                + "</tr>";
    }

    private static String fila(String descripcion, String detalle, String cantidad,
                               BigDecimal unitario, BigDecimal total, boolean cargo) {
        return "<tr" + (cargo ? " class=\"cargo\"" : "") + ">"
                + "<td class=\"primera\"><div class=\"desc\">" + descripcion + "</div>"
                + (detalle.isBlank() ? "" : "<div class=\"det\">" + detalle + "</div>") + "</td>"
                + "<td class=\"c\">" + cantidad + "</td>"
                + "<td class=\"r\">" + dinero(unitario) + "</td>"
                + "<td class=\"r fuerte\">" + dinero(total) + "</td>"
                + "</tr>";
    }

    /**
     * Total por línea que ve el cliente en modo detallado. La ganancia, el
     * ajuste de cierre y el redondeo no salen como renglón propio: se reparten
     * entre las líneas en proporción a su precio. Los cargos sí van aparte, así
     * que se restan de la base. La última línea absorbe los centavos.
     */
    static Map<Long, BigDecimal> totalesAlCliente(Quote q, QuoteChargeDtos.Summary r) {
        Map<Long, BigDecimal> out = new java.util.HashMap<>();
        BigDecimal lineas = q.getItems().stream().map(i -> cero(i.getLineTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (r == null || r.adjustedBase() == null || lineas.signum() <= 0) return out;
        BigDecimal cargos = cero(r.otherCharges()).add(cero(r.operatingExpenses()));
        BigDecimal objetivo = r.adjustedBase().subtract(cargos);
        if (objetivo.signum() <= 0) return out;
        BigDecimal acumulado = BigDecimal.ZERO;
        List<QuoteItem> items = q.getItems();
        for (int k = 0; k < items.size(); k++) {
            QuoteItem i = items.get(k);
            BigDecimal valor = k == items.size() - 1
                    ? objetivo.subtract(acumulado)
                    : cero(i.getLineTotal()).multiply(objetivo).divide(lineas, 2, RoundingMode.HALF_UP);
            acumulado = acumulado.add(valor);
            out.put(i.getId(), valor.setScale(2, RoundingMode.HALF_UP));
        }
        return out;
    }

    private static String textoNombre(QuoteItem i) {
        return i.getItemName() != null && !i.getItemName().isBlank() ? i.getItemName().trim()
                : (i.getProduct() != null ? i.getProduct().getName().trim() : "");
    }

    private static String nombre(QuoteItem i) {
        String base = i.getItemName() != null && !i.getItemName().isBlank()
                ? i.getItemName()
                : (i.getProduct() != null ? i.getProduct().getName() : "Línea de cotización");
        return esc(base);
    }

    // ── Logo ──────────────────────────────────────────────────────────────

    private record LogoEnCache(String img, Instant vence) {}

    /**
     * El logo configurado, incrustado y escalado para caber en 48×48 sin
     * deformarse. Si no hay, no se puede bajar o el formato no lo lee Java
     * (SVG, WEBP), va el recuadro «LOGO» como en el front.
     */
    private String logo(Empresa e) {
        String img = e.logoUrl().isBlank() ? null : logoIncrustado(e.logoUrl());
        if (img == null) {
            return "<table style=\"width:auto\"><tr><td class=\"logo-vacio\">LOGO</td></tr></table>";
        }
        return "<table style=\"width:auto\"><tr><td class=\"logo\">" + img + "</td></tr></table>";
    }

    private String logoIncrustado(String url) {
        LogoEnCache previo = logos.get(url);
        if (previo != null && previo.vence().isAfter(Instant.now())) return previo.img();
        String img = descargarLogo(url);
        // Tambien se cachea el fallo: un logo roto no debe frenar cada PDF.
        logos.put(url, new LogoEnCache(img, Instant.now().plus(LOGO_VIGENCIA)));
        return img;
    }

    private String descargarLogo(String url) {
        try {
            HttpResponse<byte[]> res = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) {
                log.warn("logo {} respondió {}", url, res.statusCode());
                return null;
            }
            byte[] bytes = res.body();
            String tipo = res.headers().firstValue("Content-Type").orElse("image/png").split(";")[0].trim();
            return incrustar(bytes, tipo, url);
        } catch (Exception ex) {
            log.warn("no se pudo bajar el logo {}: {}", url, ex.getMessage());
            return null;
        }
    }

    /** La imagen escalada para caber en 48×48, como data URI; null si Java no la lee. */
    private String incrustar(byte[] bytes, String tipo, String origen) {
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(bytes));
            if (imagen == null) {
                log.warn("logo {} en un formato que el PDF no lee (usa PNG o JPG)", origen);
                return null;
            }
            double escala = Math.min((double) LOGO_PX / imagen.getWidth(), (double) LOGO_PX / imagen.getHeight());
            int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * escala));
            int alto = Math.max(1, (int) Math.round(imagen.getHeight() * escala));
            if (!tipo.startsWith("image/")) tipo = "image/png";
            return "<img src=\"data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(bytes)
                    + "\" style=\"width:" + ancho + "px; height:" + alto + "px\" alt=\"logo\" />";
        } catch (Exception ex) {
            log.warn("no se pudo leer el logo {}: {}", origen, ex.getMessage());
            return null;
        }
    }

    // ── Formato ───────────────────────────────────────────────────────────

    /** #rrggbb válido o el de respaldo: un color mal escrito no rompe el PDF. */
    static String color(String valor, String porDefecto) {
        String v = valor == null ? "" : valor.trim();
        if (!v.startsWith("#")) v = "#" + v;
        return v.matches("#[0-9a-fA-F]{6}") ? v.toLowerCase(Locale.ROOT) : porDefecto;
    }

    /** El tinte suave del front (rgba con alfa) ya mezclado sobre blanco. */
    static String tinte(String hex, double alfa) {
        int n = Integer.parseInt(hex.substring(1), 16);
        int[] rgb = {(n >> 16) & 255, (n >> 8) & 255, n & 255};
        StringBuilder sb = new StringBuilder("#");
        for (int c : rgb) sb.append(String.format("%02x", (int) Math.round(c * alfa + 255 * (1 - alfa))));
        return sb.toString();
    }

    /** "3 días" (como la cotización en papel), o la fecha si no hay emisión. */
    static String vigenciaPrincipal(Quote q) {
        if (q.getValidUntil() == null) return "—";
        if (q.getQuoteDate() == null) return "Hasta " + fechaLarga(q.getValidUntil());
        long dias = java.time.temporal.ChronoUnit.DAYS.between(q.getQuoteDate(), q.getValidUntil());
        return dias <= 0 ? "Hasta " + fechaLarga(q.getValidUntil()) : dias + (dias == 1 ? " día" : " días");
    }

    /** Debajo: hasta qué fecha, o «Propuesta comercial» si ya está arriba. */
    static String vigenciaDetalle(Quote q) {
        if (q.getValidUntil() == null || q.getQuoteDate() == null
                || !q.getValidUntil().isAfter(q.getQuoteDate())) return "Propuesta comercial";
        return "Hasta el " + fechaLarga(q.getValidUntil());
    }

    static String fechaLarga(LocalDate fecha) {
        return esc(fecha.format(FECHA_LARGA));
    }

    private static String vacio(String v, String porDefecto) {
        return v == null || v.isBlank() ? porDefecto : v;
    }

    private static BigDecimal cero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static String dinero(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "Q " + f.format(cero(v).setScale(2, RoundingMode.HALF_UP));
    }

    private static String num(BigDecimal v) {
        return cero(v).stripTrailingZeros().toPlainString();
    }

    private static String br(String texto) {
        return esc(texto).lines().collect(Collectors.joining("<br />"));
    }

    /** XHTML no perdona un & suelto ni un < en el nombre de un cliente. */
    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
