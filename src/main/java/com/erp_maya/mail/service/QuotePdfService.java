package com.erp_maya.mail.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.settings.repository.CompanySettingRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.inject.Singleton;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Genera el PDF de la cotización en el servidor.
 *
 * El navegador ya sabía armarlo (quotePdf.js), pero abre una ventana de
 * impresión: no produce un archivo que se pueda adjuntar, y el envío
 * automático ocurre sin que nadie tenga una pantalla abierta.
 *
 * El marcado es XHTML estricto y la maquetación va con tablas: openhtmltopdf
 * no resuelve flexbox ni grid, y un div mal cerrado no avisa, revienta.
 */
@Singleton
public class QuotePdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TINTA = "#7a1f2b";      // corinto de la marca
    private static final String GRIS = "#6b6b6b";

    private final CompanyRepository companies;
    private final CompanySettingRepository settings;
    private final TenantContext tenant;
    private final QuoteRepository quotes;

    public QuotePdfService(CompanyRepository companies, CompanySettingRepository settings,
                           TenantContext tenant, QuoteRepository quotes) {
        this.companies = companies;
        this.settings = settings;
        this.tenant = tenant;
        this.quotes = quotes;
    }

    /** El PDF de una cotización de la empresa actual, o vacío si no existe. */
    public Optional<byte[]> generar(Long quoteId) {
        // Con fetch join: recorrer las líneas de una entidad desprendida revienta.
        return quotes.findWithItems(quoteId, tenant.getCompanyId()).map(q -> generar(q));
    }

    public byte[] generar(Quote q) {
        Long companyId = tenant.getCompanyId();
        Company empresa = companies.findById(companyId).orElse(null);
        String nombreEmpresa = empresa != null ? empresa.getName() : "";
        String nitEmpresa = empresa != null && empresa.getNit() != null ? empresa.getNit() : "";
        String color = ajuste(companyId, "brand.primary_color", TINTA);

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(xhtml(q, nombreEmpresa, nitEmpresa, color), null);
            b.toStream(salida);
            b.run();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de la cotización: " + e.getMessage(), e);
        }
    }

    private String ajuste(Long companyId, String clave, String porDefecto) {
        return settings.findByCompanyIdAndSettingKey(companyId, clave)
                .map(s -> s.getSettingValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(porDefecto);
    }

    /**
     * Un prospecto lo armó el asistente y nadie revisó sus precios: el PDF lo
     * dice arriba, antes de las líneas, para que nadie lo tome como oferta firme.
     */
    private static String marcarPreliminar(String html) {
        String aviso = """
            <table style="margin:0 0 12pt; border:1pt solid #e0b84c; background:#fff8e1"><tr>
              <td style="padding:8pt 10pt; font-size:9pt; color:#5c4400">
                <b>Cotización preliminar.</b> Fue preparada por nuestro asistente y aún debe ser
                revisada y aprobada por un agente de ventas. Precios, existencias y condiciones
                pueden cambiar hasta su confirmación.
              </td>
            </tr></table>
            """;
        return html.replace("<h1>COTIZACIÓN</h1>", "<h1>COTIZACIÓN PRELIMINAR</h1>")
                   .replace("<table class=\"lineas\">", aviso + "<table class=\"lineas\">");
    }

    private static boolean esProspecto(Quote q) {
        return "prospecto".equalsIgnoreCase(q.getStatus()) || "abierta".equalsIgnoreCase(q.getStatus());
    }

    private String xhtml(Quote q, String empresa, String nit, String color) {
        StringBuilder filas = new StringBuilder();
        for (QuoteItem i : q.getItems()) {
            filas.append("<tr>")
                 .append(td(descripcion(i), "left", false))
                 .append(td(num(i.getQuantity()), "right", false))
                 .append(td(dinero(i.getUnitPrice()), "right", false))
                 .append(td(dinero(i.getLineTotal()), "right", true))
                 .append("</tr>");
        }
        if (q.getItems().isEmpty()) {
            filas.append("<tr><td colspan=\"4\" class=\"vacio\">Sin líneas</td></tr>");
        }

        String fecha = (q.getQuoteDate() == null ? LocalDate.now() : q.getQuoteDate()).format(FECHA);
        String vence = q.getValidUntil() == null ? "—" : q.getValidUntil().format(FECHA);

        String html = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
            <style>
              @page { size: letter; margin: 30pt 34pt; }
              body { font-family: Helvetica, Arial, sans-serif; font-size: 9.5pt; color: #222; }
              .marca { color: %s; }
              h1 { font-size: 16pt; margin: 0 0 2pt; }
              .sub { color: %s; font-size: 8.5pt; }
              table { width: 100%%; border-collapse: collapse; }
              .cab td { padding: 0 0 14pt; vertical-align: top; }
              .lineas th { background: #f2f2f3; padding: 6pt; font-size: 8.5pt;
                           text-align: left; border-bottom: 1pt solid #ddd; }
              .lineas th.r { text-align: right; }
              .lineas td { padding: 6pt; border-bottom: 0.6pt solid #eee; }
              .r { text-align: right; } .b { font-weight: bold; }
              .vacio { padding: 10pt; color: %s; }
              .totales td { padding: 3pt 6pt; }
              .totales .fin td { border-top: 1pt solid #ddd; font-weight: bold; font-size: 11pt; }
              .bloque { margin-top: 14pt; font-size: 8.5pt; color: %s; }
              .pie { margin-top: 22pt; border-top: 0.6pt solid #ddd; padding-top: 6pt;
                     font-size: 7.5pt; color: %s; }
            </style></head>
            <body>
              <table class="cab"><tr>
                <td>
                  <h1 class="marca">%s</h1>
                  <div class="sub">%s</div>
                </td>
                <td class="r">
                  <h1>COTIZACIÓN</h1>
                  <div class="sub">%s</div>
                  <div class="sub">Fecha: %s</div>
                  <div class="sub">Válida hasta: <b>%s</b></div>
                </td>
              </tr></table>

              <table class="cab"><tr>
                <td>
                  <div class="sub">CLIENTE</div>
                  <div><b>%s</b></div>
                  <div class="sub">%s</div>
                  <div class="sub">%s</div>
                </td>
              </tr></table>

              <table class="lineas">
                <thead><tr>
                  <th>Descripción</th><th class="r">Cant.</th>
                  <th class="r">Precio</th><th class="r">Total</th>
                </tr></thead>
                <tbody>%s</tbody>
              </table>

              <table class="totales" style="margin-top:10pt">
                <tr><td class="r">Subtotal</td><td class="r" style="width:110pt">%s</td></tr>
                <tr><td class="r">IVA (%s%%)</td><td class="r">%s</td></tr>
                <tr class="fin"><td class="r">TOTAL</td><td class="r">%s</td></tr>
              </table>

              %s
              %s
              <div class="pie">%s%s</div>
            </body></html>
            """.formatted(
                color, GRIS, GRIS, GRIS, GRIS,
                esc(empresa), nit.isBlank() ? "" : "NIT " + esc(nit),
                esc(q.getDocNumber()), fecha, vence,
                esc(vacio(q.getClientName(), "—")),
                q.getClientNit() == null || q.getClientNit().isBlank() ? "" : "NIT " + esc(q.getClientNit()),
                esc(vacio(q.getClientEmail(), "")),
                filas,
                dinero(q.getSubtotal()), num(q.getTaxRate()), dinero(q.getTax()), dinero(q.getTotal()),
                bloque("Condiciones de pago", q.getPaymentTerms()),
                bloque("Notas", q.getNotes()),
                esc(empresa), nit.isBlank() ? "" : " · NIT " + esc(nit));
        return esProspecto(q) ? marcarPreliminar(html) : html;
    }

    private static String td(String v, String alineacion, boolean negrita) {
        return "<td class=\"" + ("right".equals(alineacion) ? "r " : "") + (negrita ? "b" : "") + "\">" + v + "</td>";
    }

    private static String descripcion(QuoteItem i) {
        String base = i.getItemName() != null && !i.getItemName().isBlank()
                ? i.getItemName()
                : (i.getProduct() != null ? i.getProduct().getName() : "Línea");
        String det = i.getDescription();
        return esc(base) + (det == null || det.isBlank()
                ? "" : "<br /><span class=\"sub\">" + esc(det) + "</span>");
    }

    private static String bloque(String titulo, String texto) {
        if (texto == null || texto.isBlank()) return "";
        return "<div class=\"bloque\"><b>" + esc(titulo) + ":</b> " + esc(texto) + "</div>";
    }

    private static String vacio(String v, String porDefecto) {
        return v == null || v.isBlank() ? porDefecto : v;
    }

    private static String dinero(BigDecimal v) {
        return "Q " + (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String num(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).stripTrailingZeros().toPlainString();
    }

    /** XHTML no perdona un & suelto ni un < en el nombre de un cliente. */
    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
