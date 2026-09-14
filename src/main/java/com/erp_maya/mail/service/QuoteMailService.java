package com.erp_maya.mail.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteHistory;
import com.erp_maya.quote.repository.QuoteHistoryRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Envía la cotización al cliente cuando pasa a «enviada».
 *
 * El envío ocurre FUERA de la transacción que cambió el estado: un SMTP lento
 * puede tardar hasta diez segundos, y sostener una conexión de base de datos
 * todo ese rato por un correo es un precio que no vale la pena.
 *
 * El resultado, salga bien o mal, queda en la bitácora de la cotización. Sin
 * eso nadie sabría si al cliente le llegó algo.
 */
@Singleton
public class QuoteMailService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final QuoteRepository quotes;
    private final QuoteHistoryRepository history;
    private final MailService mail;
    private final QuotePdfService pdf;
    private final CompanyRepository companies;
    private final TenantContext tenant;

    public QuoteMailService(QuoteRepository quotes, QuoteHistoryRepository history,
                            MailService mail, QuotePdfService pdf,
                            CompanyRepository companies, TenantContext tenant) {
        this.quotes = quotes;
        this.history = history;
        this.mail = mail;
        this.pdf = pdf;
        this.companies = companies;
        this.tenant = tenant;
    }

    /**
     * Manda la cotización si hay a quién y con qué. Nunca lanza: el cambio de
     * estado ya se guardó y un fallo de correo no puede deshacerlo ni
     * presentarse como si la operación hubiera fallado.
     */
    public MailService.Resultado enviar(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        // Con fetch join: este método corre sin transacción y recorrer las
        // líneas de una entidad desprendida reventaría.
        Quote q = quotes.findWithItems(quoteId, companyId).orElse(null);
        if (q == null) return new MailService.Resultado(false, "Cotización no encontrada.");

        // Solo a clientes. Una RFQ va a un proveedor, que ni siquiera tiene
        // correo registrado en el sistema todavía.
        if (!"client".equalsIgnoreCase(q.getPartyType())) {
            return new MailService.Resultado(false, "Las RFQ a proveedor no se envían por correo.");
        }
        String para = q.getClientEmail();
        if (para == null || para.isBlank()) {
            return anotar(companyId, quoteId, new MailService.Resultado(false,
                    "El cliente no tiene correo registrado en la cotización."));
        }
        if (!mail.activo()) {
            return anotar(companyId, quoteId, new MailService.Resultado(false,
                    "El correo saliente está desactivado; la cotización no se envió."));
        }

        String empresa = companies.findById(companyId)
                .map(c -> c.getName()).filter(n -> n != null && !n.isBlank())
                .orElse("nuestra empresa");
        byte[] adjunto;
        try {
            adjunto = pdf.generar(q);
        } catch (RuntimeException e) {
            // Sin PDF no se manda: el cuerpo dice «adjunto encontrará» y un
            // correo que promete un archivo que no lleva es peor que ninguno.
            return anotar(companyId, quoteId, new MailService.Resultado(false,
                    "No se pudo generar el PDF: " + e.getMessage()));
        }
        String asunto = "Cotización " + q.getDocNumber() + " · " + empresa;
        return anotar(companyId, quoteId,
                mail.enviar(para, asunto, cuerpo(q, empresa), adjunto,
                        "Cotizacion-" + q.getDocNumber() + ".pdf"));
    }

    /**
     * La bitácora es el único rastro de si el correo salió.
     *
     * Sin @Transactional a propósito: se llama desde `enviar` en este mismo
     * bean, así que la anotación no pasaría por el proxy y no haría nada. El
     * save del repositorio abre la suya.
     */
    private MailService.Resultado anotar(Long companyId, Long quoteId, MailService.Resultado r) {
        history.save(new QuoteHistory(companyId, quoteId,
                (r.ok() ? "Correo enviado · " : "Correo NO enviado · ") + r.mensaje(), null));
        return r;
    }

    /**
     * Cuerpo del mensaje: texto informativo, no el detalle.
     *
     * El detalle va en el PDF adjunto. Repetirlo en el correo obliga a
     * mantener dos maquetaciones del mismo documento y abre la puerta a que
     * digan cosas distintas.
     */
    private String cuerpo(Quote q, String empresa) {
        String vence = q.getValidUntil() == null ? null : q.getValidUntil().format(FECHA);
        LocalDate desde = q.getQuoteDate() == null ? LocalDate.now() : q.getQuoteDate();
        Long dias = q.getValidUntil() == null ? null : ChronoUnit.DAYS.between(desde, q.getValidUntil());

        // La vigencia se dice en días solo si el número tiene sentido: una
        // cotización ya vencida daría un negativo y quedaría peor que callarlo.
        String vigencia = (dias != null && dias > 0)
                ? "Esta cotización tiene una vigencia de <strong>" + dias
                  + (dias == 1 ? " día" : " días") + "</strong> y expira el <strong>" + vence + "</strong>."
                : (vence != null
                    ? "Esta cotización expira el <strong>" + vence + "</strong>."
                    : "");

        return """
            <div style="font-family:Arial,Helvetica,sans-serif;color:#222;font-size:14px;max-width:560px">
              <p>Estimado/a %s:</p>
              <p>
                Adjunto encontrará la cotización <strong>%s</strong>, acordada con
                <strong>%s</strong>, con el detalle de los bienes y servicios convenidos.
              </p>
              <p>%s</p>
              <p>
                Si tiene alguna consulta sobre el contenido o desea realizar algún ajuste,
                puede responder directamente a este correo.
              </p>
              <p style="margin-top:22px">Atentamente,<br /><strong>%s</strong></p>
            </div>
            """.formatted(
                esc(vacio(q.getClientName(), "cliente")),
                esc(q.getDocNumber()),
                esc(empresa),
                vigencia,
                esc(empresa));
    }

    private static String vacio(String v, String porDefecto) {
        return v == null || v.isBlank() ? porDefecto : v;
    }






    /** El nombre del cliente y las notas los escribe una persona: pueden traer &lt; o &amp;. */
    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
