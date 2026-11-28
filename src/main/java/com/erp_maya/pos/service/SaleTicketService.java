package com.erp_maya.pos.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.domain.Company;
import com.erp_maya.company.domain.Establishment;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.fel.domain.FelDocument;
import com.erp_maya.fel.repository.FelDocumentRepository;
import com.erp_maya.mail.service.MailService;
import com.erp_maya.pos.domain.Sale;
import com.erp_maya.pos.domain.SaleItem;
import com.erp_maya.pos.dto.SaleDtos;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.settings.repository.CompanySettingRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * El ticket de una venta del POS: lo que se imprime, se descarga en PDF o se
 * manda por correo al terminar la venta.
 *
 * Se arma aquí, con los datos guardados, y no en la pantalla: el ticket de la
 * pantalla traía la empresa, el NIT, el cajero y la autorización SAT escritos
 * a mano, y un comprobante con datos fiscales inventados no se le entrega a
 * un cliente. Empresa y NIT salen de la configuración (las mismas claves que
 * el PDF de la cotización), la sucursal y el cajero de la venta, el DTE de su
 * certificación.
 */
@Singleton
public class SaleTicketService {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SaleRepository sales;
    private final FelDocumentRepository felDocuments;
    private final CompanyRepository companies;
    private final CompanySettingRepository settings;
    private final MailService mail;
    private final TenantContext tenant;

    public SaleTicketService(SaleRepository sales, FelDocumentRepository felDocuments,
                             CompanyRepository companies, CompanySettingRepository settings,
                             MailService mail, TenantContext tenant) {
        this.sales = sales;
        this.felDocuments = felDocuments;
        this.companies = companies;
        this.settings = settings;
        this.mail = mail;
        this.tenant = tenant;
    }

    @Transactional
    public SaleDtos.Ticket ticket(Long saleId) {
        Long companyId = tenant.getCompanyId();
        Sale s = sales.findByIdAndCompanyId(saleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Venta " + saleId + " no encontrada"));
        Company c = companies.findById(companyId).orElse(null);
        Branch b = s.getBranch();
        Establishment est = b != null ? b.getEstablishment() : null;

        // El nombre del ticket es el comercial del establecimiento; la razón
        // social va debajo solo si es distinta.
        String razon = c != null && c.getName() != null ? c.getName() : "";
        String comercial = est != null && noVacio(est.getCommercialName()) ? est.getCommercialName().trim() : razon;
        String nitTabla = c != null && c.getNit() != null ? c.getNit() : "";
        String direccion = primero(b != null ? b.getAddress() : null,
                est != null ? est.getAddress() : null, ajuste(companyId, "company.address"));
        String telefono = primero(est != null ? est.getPhone() : null, ajuste(companyId, "company.phone"));

        // El subtotal del ticket es la suma de las líneas tal como se cobraron
        // (IVA incluido). sales.subtotal guarda la base sin IVA y, impreso
        // arriba del total, se leería como si faltara dinero.
        BigDecimal sumaLineas = BigDecimal.ZERO;
        List<SaleDtos.TicketLine> lineas = new ArrayList<>();
        for (SaleItem i : s.getItems()) {
            sumaLineas = sumaLineas.add(cero(i.getLineTotal()));
            String nombre = i.getProduct() != null ? i.getProduct().getName()
                    : (noVacio(i.getConcept()) ? i.getConcept() : "Servicio");
            String unidad = i.getProduct() != null ? i.getProduct().getUnit() : null;
            lineas.add(new SaleDtos.TicketLine(nombre, i.getQuantity(), unidad, i.getUnitPrice(),
                    i.getDiscount(), i.getLineTotal()));
        }

        BigDecimal total = cero(s.getTotal());
        BigDecimal iva = cero(s.getTax());
        SaleDtos.TicketFel fel = felDocuments.findBySaleIdAndCompanyIdOrderByIdDesc(s.getId(), companyId).stream()
                .filter(f -> f.getCertifiedAt() != null || noVacio(f.getAuthorizationNumber()))
                .findFirst()
                .map(f -> fel(f, ajuste(companyId, "fel.resolution")))
                .orElse(null);

        return new SaleDtos.Ticket(s.getId(), s.getDocType(), s.getDocNumber(), s.getSaleDate(),
                comercial, comercial.equals(razon) ? null : razon,
                primero(ajuste(companyId, "company.nit"), nitTabla),
                direccion, telefono, ajuste(companyId, "company.email"),
                b != null ? b.getName() : null,
                s.getUser() != null ? s.getUser().getName() : null,
                s.getClient() != null ? s.getClient().getName() : "Consumidor final",
                s.getClient() != null && noVacio(s.getClient().getNit()) ? s.getClient().getNit() : "CF",
                s.getClient() != null ? s.getClient().getEmail() : null,
                lineas, sumaLineas, cero(s.getDiscountTotal()), total.subtract(iva), iva,
                s.getTaxRate(), total, s.getPaymentMethod(), s.isCredit(), fel,
                s.getStatus(), s.getVoidedAt(), s.getVoidReason());
    }

    /**
     * El ticket en PDF, del ancho de un rollo de 80 mm.
     *
     * Recibe el ticket ya armado y no el id: {@link #ticket} es la única parte
     * que necesita transacción, y llamarla desde aquí no pasaría por el proxy
     * de Micronaut (las líneas de la venta reventarían al leerse).
     */
    public byte[] pdf(SaleDtos.Ticket t) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(xhtml(t), null);
            b.toStream(salida);
            b.run();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del ticket: " + e.getMessage(), e);
        }
    }

    /** Manda el ticket en PDF al correo indicado, con el SMTP de la empresa. */
    public MailService.Resultado enviar(SaleDtos.Ticket t, String para) {
        byte[] adjunto = pdf(t);
        String doc = nombreDocumento(t);
        String asunto = doc + " · " + t.companyName();
        String cuerpo = "<p>Hola" + (noVacio(t.clientName()) && !"Consumidor final".equals(t.clientName())
                ? " " + esc(t.clientName()) : "") + ",</p>"
                + "<p>Adjuntamos el comprobante de su compra <b>" + esc(doc) + "</b> por <b>"
                + esc(quetzales(t.total())) + "</b>.</p>"
                + "<p>¡Gracias por su compra!<br>" + esc(t.companyName()) + "</p>";
        return mail.enviar(para.trim(), asunto, cuerpo, adjunto, archivo(t));
    }

    /** Nombre del archivo PDF: el número del documento, sin caracteres raros. */
    public static String archivo(SaleDtos.Ticket t) {
        String base = noVacio(t.docNumber()) ? t.docNumber() : "venta-" + t.saleId();
        return "Ticket-" + base.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    // ── Documento ─────────────────────────────────────────────────────────

    /**
     * El ticket en XHTML para openhtmltopdf. Monoespaciada como un rollo
     * térmico; el alto de la página sale de cuántos renglones hay, para que no
     * quede un metro de papel en blanco.
     */
    public static String xhtml(SaleDtos.Ticket t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html><head><meta charset=\"UTF-8\"/><style>")
          .append("@page { size: 80mm ").append(altoMm(t)).append("mm; margin: 4mm 4mm; }")
          .append("body { font-family: monospace; font-size: 8.5pt; color: #111; margin: 0; }")
          .append(".c { text-align: center; } .b { font-weight: bold; } .s { font-size: 7.5pt; color: #444; }")
          .append(".h { font-size: 11pt; font-weight: bold; } .sep { border-top: 1px dashed #777; margin: 5px 0; }")
          .append("table { width: 100%; border-collapse: collapse; } td { padding: 1px 0; vertical-align: top; }")
          .append("td.r { text-align: right; white-space: nowrap; } .tot td { font-weight: bold; font-size: 10.5pt; }")
          .append("</style></head><body>");

        sb.append("<div class=\"c\"><div class=\"h\">").append(esc(t.companyName())).append("</div>");
        if (noVacio(t.legalName())) sb.append("<div>").append(esc(t.legalName())).append("</div>");
        if (noVacio(t.branchName())) sb.append("<div>").append(esc(t.branchName())).append("</div>");
        if (noVacio(t.companyAddress())) sb.append("<div>").append(esc(t.companyAddress())).append("</div>");
        List<String> contacto = new ArrayList<>();
        if (noVacio(t.companyNit())) contacto.add("NIT " + t.companyNit());
        if (noVacio(t.companyPhone())) contacto.add("Tel " + t.companyPhone());
        if (!contacto.isEmpty()) sb.append("<div>").append(esc(String.join(" · ", contacto))).append("</div>");
        sb.append("</div><div class=\"sep\"></div>");
        // Una venta anulada se sigue pudiendo imprimir (auditoría), pero que no
        // se confunda con un comprobante vigente.
        if ("cancelled".equalsIgnoreCase(t.status())) {
            sb.append("<div class=\"c h\">*** ANULADA ***</div>");
            if (noVacio(t.voidReason())) sb.append("<div class=\"c s\">Motivo: ").append(esc(t.voidReason())).append("</div>");
            sb.append("<div class=\"sep\"></div>");
        }
        sb.append("<table>");

        fila(sb, nombreTipo(t.docType()), t.docNumber());
        fila(sb, "Fecha", t.saleDate() != null ? FECHA.format(t.saleDate().atZone(GUATEMALA)) : "");
        if (noVacio(t.cashier())) fila(sb, "Cajero", t.cashier());
        fila(sb, "Cliente", t.clientName());
        fila(sb, "NIT", t.clientNit());
        sb.append("</table><div class=\"sep\"></div><table>");
        sb.append("<tr class=\"b\"><td>DESCRIPCIÓN</td><td class=\"r\">TOTAL</td></tr>");
        for (SaleDtos.TicketLine l : t.lines()) {
            sb.append("<tr><td>").append(esc(l.description())).append("</td><td class=\"r\">")
              .append(esc(quetzales(l.total()))).append("</td></tr>");
            sb.append("<tr><td class=\"s\" colspan=\"2\">").append(esc(num(l.quantity())))
              .append(' ').append(esc(noVacio(l.unit()) ? l.unit() : "u")).append(" x ")
              .append(esc(quetzales(l.unitPrice())));
            if (l.discount() != null && l.discount().signum() > 0) {
                sb.append(" · desc. ").append(esc(quetzales(l.discount())));
            }
            sb.append("</td></tr>");
        }
        sb.append("</table><div class=\"sep\"></div><table>");
        fila(sb, "Subtotal", quetzales(t.subtotal()));
        if (t.discountTotal() != null && t.discountTotal().signum() > 0) {
            fila(sb, "Descuento", "-" + quetzales(t.discountTotal()));
        }
        fila(sb, "Gravable", quetzales(t.taxableBase()));
        fila(sb, "IVA " + num(t.taxRate()) + "%", quetzales(t.tax()));
        sb.append("</table><div class=\"sep\"></div><table><tr class=\"tot\"><td>TOTAL</td><td class=\"r\">")
          .append(esc(quetzales(t.total()))).append("</td></tr></table>");
        sb.append("<div class=\"sep\"></div><table>");
        fila(sb, "Pago", t.credit() ? "CRÉDITO" : (noVacio(t.paymentMethod()) ? t.paymentMethod().toUpperCase(Locale.ROOT) : "—"));
        sb.append("</table><div class=\"sep\"></div>");

        sb.append("<div class=\"c s\">");
        if (t.fel() != null) {
            SaleDtos.TicketFel f = t.fel();
            sb.append("DTE-FEL ").append(esc(nn(f.dteType())));
            if (noVacio(f.series())) sb.append(" · Serie ").append(esc(f.series()));
            if (noVacio(f.number())) sb.append(" · No. ").append(esc(f.number()));
            if (noVacio(f.authorization())) sb.append("<br/>Autorización SAT: ").append(esc(f.authorization()));
            if (noVacio(f.resolution())) sb.append("<br/>Resol. SAT ").append(esc(f.resolution()));
        } else {
            sb.append("Documento sin certificación FEL");
        }
        sb.append("</div><div class=\"sep\"></div><div class=\"c\">¡GRACIAS POR SU COMPRA!");
        if (noVacio(t.companyEmail())) sb.append("<br/><span class=\"s\">").append(esc(t.companyEmail())).append("</span>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    /** Alto del rollo en mm: cabecera, pie y dos renglones por línea vendida. */
    static int altoMm(SaleDtos.Ticket t) {
        int lineas = t.lines() == null ? 0 : t.lines().size();
        return Math.min(2000, 150 + lineas * 9 + (t.fel() != null ? 10 : 0));
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────

    private static SaleDtos.TicketFel fel(FelDocument f, String resolucion) {
        return new SaleDtos.TicketFel(f.getDteType(), f.getSeries(), f.getNumber(),
                f.getAuthorizationNumber(), f.getCertifiedAt(), noVacio(resolucion) ? resolucion : null);
    }

    private String ajuste(Long companyId, String clave) {
        return settings.findByCompanyIdAndSettingKey(companyId, clave)
                .map(s -> s.getSettingValue())
                .filter(SaleTicketService::noVacio)
                .map(String::trim)
                .orElse(null);
    }

    private static void fila(StringBuilder sb, String izq, String der) {
        sb.append("<tr><td>").append(esc(izq)).append("</td><td class=\"r\">").append(esc(der)).append("</td></tr>");
    }

    static String nombreTipo(String docType) {
        if (docType == null) return "VENTA";
        return switch (docType.toUpperCase(Locale.ROOT)) {
            case "FACT", "FCAM" -> "FACTURA";
            case "NCRE" -> "NOTA DE CRÉDITO";
            case "NDEB" -> "NOTA DE DÉBITO";
            case "RECI" -> "RECIBO";
            default -> docType.toUpperCase(Locale.ROOT);
        };
    }

    private static String nombreDocumento(SaleDtos.Ticket t) {
        String tipo = nombreTipo(t.docType());
        String titulo = tipo.charAt(0) + tipo.substring(1).toLowerCase(Locale.ROOT);
        return noVacio(t.docNumber()) ? titulo + " " + t.docNumber() : titulo;
    }

    static String quetzales(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "Q " + f.format(cero(v).setScale(2, RoundingMode.HALF_UP));
    }

    private static String num(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal cero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String primero(String... vs) {
        for (String v : vs) if (noVacio(v)) return v.trim();
        return null;
    }

    private static boolean noVacio(String s) { return s != null && !s.isBlank(); }

    private static String nn(String s) { return s == null ? "" : s; }

    static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
