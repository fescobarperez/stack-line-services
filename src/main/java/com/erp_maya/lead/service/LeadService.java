package com.erp_maya.lead.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.lead.domain.Lead;
import com.erp_maya.lead.dto.LeadDtos;
import com.erp_maya.lead.repository.LeadRepository;
import com.erp_maya.mail.domain.CompanyMailSettings;
import com.erp_maya.mail.service.MailService;
import com.erp_maya.mail.service.MailSettingsService;
import com.erp_maya.settings.domain.CompanySetting;
import com.erp_maya.settings.repository.CompanySettingRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prospectos. Los registra TINO cuando un visitante deja sus datos y avisa
 * por correo a la empresa con su propio SMTP.
 *
 * El aviso va a la configuración {@code leads.notify_email}; si no existe,
 * al remitente del correo de la empresa. Si el correo falla, el prospecto
 * igual queda guardado: lo importante es no perder el contacto.
 */
@Singleton
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    public static final String SETTING_NOTIFY = "leads.notify_email";
    static final Set<String> STATUSES = Set.of("nuevo", "contactado", "descartado", "convertido");
    static final Set<String> SOURCES = Set.of("manual", "tino_web", "tino_whatsapp", "web_form");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Map<String, String> INTERESES = Map.of(
            "stackline", "Stackline",
            "tino", "TINO",
            "software_a_medida", "Software a medida",
            "otro", "Otro");

    private final LeadRepository leads;
    private final CompanySettingRepository settings;
    private final MailSettingsService mailSettings;
    private final MailService mail;
    private final TenantContext tenant;

    public LeadService(LeadRepository leads, CompanySettingRepository settings, MailSettingsService mailSettings,
                       MailService mail, TenantContext tenant) {
        this.leads = leads;
        this.settings = settings;
        this.mailSettings = mailSettings;
        this.mail = mail;
        this.tenant = tenant;
    }

    /**
     * Crea el prospecto, o actualiza el de la misma conversación (sourceRef).
     * Solo avisa por correo al crearlo, o si antes no pedía demo y ahora sí.
     */
    @Transactional
    public LeadDtos.Response register(LeadDtos.Create req) {
        Long companyId = tenant.getCompanyId();
        String phone = limpio(req.phone());
        String email = limpio(req.email());
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                throw new IllegalStateException("El correo no es válido.");
            }
        }
        if (phone == null && email == null) {
            throw new IllegalStateException("Hace falta un teléfono o un correo para contactar al prospecto.");
        }
        String source = limpio(req.source());
        if (source == null || !SOURCES.contains(source)) source = "manual";
        String ref = limpio(req.sourceRef());
        boolean demo = Boolean.TRUE.equals(req.wantsDemo());

        Lead previo = ref == null ? null : leads.findByCompanyIdAndSourceRef(companyId, ref).orElse(null);
        boolean nuevo = previo == null;
        boolean avisar = nuevo || (demo && !previo.isWantsDemo());

        Lead l = nuevo ? new Lead() : previo;
        Instant ahora = Instant.now();
        l.setCompanyId(companyId);
        l.setName(req.name().trim());
        // En una actualización no se borra lo que ya se sabía.
        if (limpio(req.companyName()) != null || nuevo) l.setCompanyName(limpio(req.companyName()));
        if (phone != null || nuevo) l.setPhone(phone);
        if (email != null || nuevo) l.setEmail(email);
        if (limpio(req.interest()) != null || nuevo) l.setInterest(interes(req.interest()));
        if (limpio(req.message()) != null || nuevo) l.setMessage(limpio(req.message()));
        l.setWantsDemo(demo || (!nuevo && previo.isWantsDemo()));
        l.setUpdatedAt(ahora);
        if (nuevo) {
            l.setSource(source);
            l.setSourceRef(ref);
            l.setStatus("nuevo");
            l.setCreatedAt(ahora);
            leads.save(l);
        } else {
            leads.update(l);
        }

        boolean enviado = avisar && avisar(l, companyId, nuevo);
        return response(l, enviado);
    }

    @Transactional
    public Page<LeadDtos.Response> list(String status, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<Lead> page = status == null || status.isBlank()
                ? leads.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                : leads.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, status.trim(), pageable);
        return page.map(l -> response(l, false));
    }

    @Transactional
    public LeadDtos.Response updateStatus(Long id, String status) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(s)) {
            throw new IllegalStateException("Estado no válido: " + status);
        }
        Lead l = leads.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Prospecto no encontrado"));
        l.setStatus(s);
        l.setUpdatedAt(Instant.now());
        leads.update(l);
        return response(l, false);
    }

    private boolean avisar(Lead l, Long companyId, boolean nuevo) {
        String para = destinatario(companyId);
        if (para == null) {
            log.info("prospecto {} sin aviso: no hay {} ni remitente de correo configurado", l.getId(), SETTING_NOTIFY);
            return false;
        }
        String asunto = (l.isWantsDemo() ? "Solicitud de demo: " : "Nuevo prospecto: ") + l.getName()
                + (l.getCompanyName() != null ? " (" + l.getCompanyName() + ")" : "");
        MailService.Resultado r = mail.enviar(para, asunto, cuerpo(l, nuevo));
        if (!r.ok()) {
            log.warn("prospecto {} guardado pero el aviso no salió: {}", l.getId(), r.mensaje());
        }
        return r.ok();
    }

    private String destinatario(Long companyId) {
        String configurado = settings.findByCompanyIdAndSettingKey(companyId, SETTING_NOTIFY)
                .map(CompanySetting::getSettingValue)
                .map(LeadService::limpio)
                .orElse(null);
        if (configurado != null) return configurado;
        CompanyMailSettings c = mailSettings.raw(companyId);
        return c == null ? null : limpio(c.getFromEmail());
    }

    static String cuerpo(Lead l, boolean nuevo) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#14141A\">");
        sb.append("<p>").append(nuevo ? ("web_form".equals(l.getSource()) ? "Llegó un nuevo contacto" : "TINO registró un nuevo prospecto") : "El prospecto ahora pide una demo")
                .append(" desde ").append(origen(l.getSource())).append(".</p>");
        sb.append("<table cellpadding=\"6\" style=\"border-collapse:collapse\">");
        fila(sb, "Nombre", l.getName());
        fila(sb, "Empresa", l.getCompanyName());
        fila(sb, "Teléfono", l.getPhone());
        fila(sb, "Correo", l.getEmail());
        fila(sb, "Interés", l.getInterest());
        fila(sb, "Quiere demo", l.isWantsDemo() ? "Sí" : "No");
        fila(sb, "Mensaje", l.getMessage());
        sb.append("</table><p style=\"color:#6B6A70\">Contáctalo lo antes posible.</p></div>");
        return sb.toString();
    }

    private static void fila(StringBuilder sb, String etiqueta, String valor) {
        if (valor == null || valor.isBlank()) return;
        sb.append("<tr><td style=\"color:#6B6A70\">").append(etiqueta).append("</td><td><strong>")
                .append(html(valor)).append("</strong></td></tr>");
    }

    private static String origen(String source) {
        return switch (source == null ? "" : source) {
            case "tino_web" -> "el chat de la página web";
            case "tino_whatsapp" -> "WhatsApp";
            case "web_form" -> "el formulario de contacto de la página";
            default -> "el sistema";
        };
    }

    static String interes(String valor) {
        String v = limpio(valor);
        if (v == null) return null;
        return INTERESES.getOrDefault(v.toLowerCase(Locale.ROOT), v);
    }

    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static LeadDtos.Response response(Lead l, boolean notified) {
        return new LeadDtos.Response(l.getId(), l.getSource(), l.getName(), l.getCompanyName(), l.getPhone(),
                l.getEmail(), l.getInterest(), l.getMessage(), l.isWantsDemo(), l.getStatus(), l.getCreatedAt(),
                l.getUpdatedAt(), notified);
    }
}
