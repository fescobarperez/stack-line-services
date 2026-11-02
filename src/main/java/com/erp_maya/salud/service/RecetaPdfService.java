package com.erp_maya.salud.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.mail.service.QuotePdfService;
import com.erp_maya.salud.domain.Professional;
import com.erp_maya.salud.dto.ClinicalDtos;
import com.erp_maya.salud.repository.ProfessionalRepository;
import com.erp_maya.settings.repository.CompanySettingRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * La receta de una consulta firmada, en PDF: encabezado con el logo y los
 * datos de la clínica, paciente y fecha, diagnóstico principal, los
 * medicamentos numerados, las indicaciones y la firma del profesional con su
 * colegiado. Mismo motor que la cotización (openhtmltopdf: maquetación de
 * tablas, colores en hex, logo incrustado).
 */
@Singleton
public class RecetaPdfService {

    private static final String INK = "#25242a";
    private static final String MUTED = "#726a72";
    private static final String BORDER = "#e8dfe3";
    private static final String PRIMARIO = "#8b1e3f";

    private final ClinicalService clinical;
    private final CompanyRepository companies;
    private final CompanySettingRepository settings;
    private final ProfessionalRepository professionals;
    private final QuotePdfService logos;
    private final TenantContext tenant;

    public RecetaPdfService(ClinicalService clinical, CompanyRepository companies, CompanySettingRepository settings,
                            ProfessionalRepository professionals, QuotePdfService logos, TenantContext tenant) {
        this.clinical = clinical;
        this.companies = companies;
        this.settings = settings;
        this.professionals = professionals;
        this.logos = logos;
        this.tenant = tenant;
    }

    public byte[] generar(SaludAccess.Actor actor, Long encounterId) {
        ClinicalDtos.EncounterView e = clinical.forPrescription(actor, encounterId);
        Long companyId = tenant.getCompanyId();
        Company c = companies.findById(companyId).orElse(null);
        Clinica clinica = new Clinica(
                c != null && c.getName() != null ? c.getName() : "",
                ajuste(companyId, "company.address"),
                ajuste(companyId, "company.phone"),
                ajuste(companyId, "company.email"),
                ajuste(companyId, "company.logo_url"),
                QuotePdfService.color(ajuste(companyId, "brand.primary_color"), PRIMARIO));
        Professional p = professionals.findByUserIdAndCompanyId(e.professionalId(), companyId).orElse(null);
        String logo = clinica.logoUrl().isBlank() ? null : logos.logoIncrustado(clinica.logoUrl());
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(xhtml(e, clinica, p != null ? p.getSpecialty() : null,
                    p != null ? p.getLicenseNumber() : null, logo), null);
            b.toStream(salida);
            b.run();
            return salida.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar la receta: " + ex.getMessage(), ex);
        }
    }

    record Clinica(String nombre, String direccion, String telefono, String correo, String logoUrl, String primario) {}

    private String ajuste(Long companyId, String clave) {
        return settings.findByCompanyIdAndSettingKey(companyId, clave)
                .map(s -> s.getSettingValue())
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .orElse("");
    }

    /** El documento. Público al paquete para probar el contenido sin generar el PDF. */
    static String xhtml(ClinicalDtos.EncounterView e, Clinica c, @Nullable String especialidad,
                        @Nullable String colegiado, @Nullable String logo) {
        String p = c.primario();
        String suave = QuotePdfService.tinte(p, 0.08);
        Instant fecha = e.signedAt() != null ? e.signedAt() : e.createdAt();
        String contacto = List.of(c.direccion(), c.telefono(), c.correo()).stream()
                .filter(s -> !s.isBlank()).map(RecetaPdfService::esc).collect(Collectors.joining(" · "));
        List<String> datosPaciente = new ArrayList<>();
        if (e.recordNumber() != null) datosPaciente.add("Expediente " + esc(e.recordNumber()));
        if (e.patientAge() != null) datosPaciente.add(e.patientAge() + (e.patientAge() == 1 ? " año" : " años"));
        ClinicalDtos.DiagnosisView dx = e.diagnoses().stream().filter(ClinicalDtos.DiagnosisView::primary).findFirst()
                .orElse(e.diagnoses().isEmpty() ? null : e.diagnoses().get(0));

        StringBuilder items = new StringBuilder();
        int n = 1;
        for (ClinicalDtos.PrescriptionView r : e.prescriptions()) {
            List<String> como = new ArrayList<>();
            if (r.dose() != null) como.add(esc(r.dose()));
            if (r.route() != null) como.add("vía " + esc(r.route()));
            if (r.frequency() != null) como.add(esc(r.frequency()));
            if (r.durationDays() != null) como.add("por " + r.durationDays() + (r.durationDays() == 1 ? " día" : " días"));
            items.append("<tr><td class=\"num\">").append(n++).append("</td><td>")
                    .append("<div class=\"med\">").append(esc(r.medication()))
                    .append(r.quantity() != null ? " <span class=\"cant\">— " + esc(r.quantity()) + "</span>" : "")
                    .append("</div>")
                    .append(como.isEmpty() ? "" : "<div class=\"como\">" + String.join(" · ", como) + "</div>")
                    .append(r.instructions() != null ? "<div class=\"ind\">" + br(r.instructions()) + "</div>" : "")
                    .append("</td></tr>");
        }
        String firmaSub = List.of(especialidad == null ? "" : esc(especialidad),
                        colegiado == null ? "" : "Colegiado " + esc(colegiado))
                .stream().filter(s -> !s.isBlank()).collect(Collectors.joining(" · "));

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="es">
            <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
            <style>
              @page { size: letter; margin: 18mm 16mm 20mm 16mm; }
              body { margin: 0; font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: %s; }
              table { width: 100%%; border-collapse: collapse; }
              .franja td { height: 6px; background: %s; font-size: 1px; line-height: 1px; }
              .clinica { font-size: 17px; font-weight: bold; color: %s; }
              .contacto { font-size: 9px; color: %s; margin-top: 3px; }
              .titulo { font-size: 20px; font-weight: bold; color: %s; text-align: right; }
              .fecha { font-size: 10px; color: %s; text-align: right; margin-top: 3px; }
              .paciente { background: %s; border: 1px solid %s; }
              .paciente td { padding: 10px 12px; }
              .ceja { font-size: 8px; font-weight: bold; letter-spacing: 1px; color: %s; }
              .nombre { font-size: 14px; font-weight: bold; margin-top: 2px; }
              .meta { font-size: 9px; color: %s; margin-top: 2px; }
              .rx { font-size: 26px; font-weight: bold; color: %s; margin: 16px 0 4px; }
              .items td { padding: 8px 6px; border-bottom: 1px solid %s; vertical-align: top; }
              .items td.num { width: 22px; font-weight: bold; color: %s; }
              .med { font-size: 12px; font-weight: bold; }
              .cant { font-weight: normal; color: %s; }
              .como { margin-top: 3px; }
              .ind { margin-top: 3px; color: %s; font-style: italic; }
              .bloque { margin-top: 14px; }
              .bloque .texto { margin-top: 4px; line-height: 1.5; }
              .firma { margin-top: 56px; }
              .firma td { text-align: center; }
              .raya { border-top: 1px solid %s; width: 240px; margin: 0 auto; padding-top: 5px; }
              .firma-nombre { font-weight: bold; }
              .firma-sub { font-size: 9px; color: %s; margin-top: 2px; }
            </style></head>
            <body>
              <table class="franja"><tr><td></td></tr></table>
              <table style="margin-top: 12px"><tr>
                <td style="width: 60px; vertical-align: middle">%s</td>
                <td style="vertical-align: middle"><div class="clinica">%s</div><div class="contacto">%s</div></td>
                <td style="vertical-align: middle"><div class="titulo">Receta médica</div><div class="fecha">%s</div></td>
              </tr></table>
              <table class="paciente" style="margin-top: 14px"><tr><td>
                <div class="ceja">PACIENTE</div>
                <div class="nombre">%s</div>
                <div class="meta">%s</div>
              </td></tr></table>
              %s
              <div class="rx">Rx</div>
              <table class="items">%s</table>
              %s
              <table class="firma"><tr><td>
                <div class="raya"><div class="firma-nombre">%s</div><div class="firma-sub">%s</div></div>
              </td></tr></table>
            </body></html>
            """.formatted(
                INK, p, INK, MUTED, p, MUTED, suave, BORDER, p, MUTED, p, BORDER, p, MUTED, MUTED, INK, MUTED,
                logo == null ? "" : logo,
                esc(c.nombre()), contacto,
                fechaLarga(fecha),
                esc(e.patientName()), String.join(" · ", datosPaciente),
                dx == null ? "" : "<div class=\"bloque\"><div class=\"ceja\">DIAGNÓSTICO</div><div class=\"texto\">"
                        + (dx.code() != null ? esc(dx.code()) + " — " : "") + esc(dx.description()) + "</div></div>",
                items,
                e.instructions() == null ? "" : "<div class=\"bloque\"><div class=\"ceja\">INDICACIONES</div><div class=\"texto\">"
                        + br(e.instructions()) + "</div></div>",
                esc(e.professionalName()), firmaSub);
    }

    /** «3 de noviembre de 2026», en hora de Guatemala. */
    static String fechaLarga(Instant i) {
        var d = i.atZone(AgendaRules.GUATEMALA).toLocalDate();
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
                "octubre", "noviembre", "diciembre"};
        return d.getDayOfMonth() + " de " + meses[d.getMonthValue() - 1] + " de " + d.getYear();
    }

    private static String br(String texto) {
        return esc(texto).lines().collect(Collectors.joining("<br />"));
    }

    /** XHTML no perdona un & suelto ni un < en un nombre. */
    static String esc(@Nullable String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
