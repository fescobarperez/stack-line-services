package com.erp_maya.salud.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.salud.domain.FollowUp;
import com.erp_maya.salud.domain.FollowUpRow;
import com.erp_maya.salud.dto.CajaDtos;
import com.erp_maya.salud.repository.FollowUpRepository;
import com.erp_maya.salud.repository.FollowUpRowRepository;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.erp_maya.salud.service.SaludAccess.AGENDA;
import static com.erp_maya.salud.service.SaludAccess.CLINICAL;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.FOLLOWUP;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Seguimiento de controles: la bandeja de recepción (vencidos y por vencer,
 * para llamar o agendar) y la respuesta del paciente al recordatorio.
 *
 * Recepción ve quién, con quién y para cuándo; el caso, las notas y cómo
 * dice sentirse el paciente son datos clínicos y solo los ve quien tiene
 * clinical_record. Sí ve que el paciente contestó, para no llamarle dos veces.
 */
@Singleton
public class FollowUpService {

    static final int MAX_DIAS = 90;
    private static final int LIMITE = 300;

    private final FollowUpRowRepository rows;
    private final FollowUpRepository followUps;
    private final TenantContext tenant;

    public FollowUpService(FollowUpRowRepository rows, FollowUpRepository followUps, TenantContext tenant) {
        this.rows = rows;
        this.followUps = followUps;
        this.tenant = tenant;
    }

    /** Controles pendientes (sin cita) que vencen en los próximos `days` días o ya vencieron. */
    @Transactional
    public List<CajaDtos.FollowUpItem> inbox(SaludAccess.Actor actor, @Nullable Integer days, @Nullable Long professionalId) {
        actor.require(FOLLOWUP, VER);
        int d = days == null ? 30 : Math.max(0, Math.min(MAX_DIAS, days));
        LocalDate hoy = PatientRules.today();
        boolean clinico = actor.can(CLINICAL, VER) && !actor.service();
        return rows.pending(tenant.getCompanyId(), hoy.plusDays(d), professionalId == null ? 0L : professionalId, LIMITE)
                .stream().map(r -> item(r, hoy, clinico)).toList();
    }

    /** Los vencidos, para el reporte. */
    @Transactional
    public List<CajaDtos.FollowUpItem> overdue(SaludAccess.Actor actor, int limite) {
        LocalDate hoy = PatientRules.today();
        boolean clinico = actor.can(CLINICAL, VER) && !actor.service();
        return rows.pending(tenant.getCompanyId(), hoy.minusDays(1), 0L, limite).stream()
                .map(r -> item(r, hoy, clinico)).toList();
    }

    /**
     * Lo que contestó el paciente. Lo manda el asistente (botones del
     * recordatorio) o recepción si el paciente lo dijo por teléfono. El
     * asistente debe mandar el teléfono del chat: tiene que ser el del paciente.
     */
    @Transactional
    public CajaDtos.FeedbackResult feedback(SaludAccess.Actor actor, Long id, CajaDtos.FeedbackRequest req) {
        actor.require(AGENDA, EDITAR);
        FollowUpRow r = rows.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Control " + id + " no encontrado"));
        if (actor.service() && !mismoTelefono(r.getPatientPhone(), req.phone())) {
            throw new SaludForbiddenException("Ese control no es de este número.");
        }
        if (!ClinicalRules.FU_PENDIENTE.equals(r.getStatus()) && !ClinicalRules.FU_AGENDADO.equals(r.getStatus())) {
            throw new IllegalStateException("Ese control ya está «" + r.getStatus() + "».");
        }
        String feeling = CajaRules.feeling(req.feeling());
        FollowUp f = followUps.findById(id).orElseThrow();
        f.setFeeling(feeling);
        f.setFeelingAt(Instant.now());
        String nota = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        f.setFeelingNote(nota == null ? null : nota.length() > 500 ? nota.substring(0, 500) : nota);
        followUps.update(f);
        return new CajaDtos.FeedbackResult(f.getId(), f.getPatientId(), r.getPatientName(), r.getProfessionalId(), r.getProfessionalName(),
                r.getTypeId(), r.getTypeName(), r.getDueOn(), f.getStatus(), feeling);
    }

    static CajaDtos.FollowUpItem item(FollowUpRow r, LocalDate hoy, boolean clinico) {
        return new CajaDtos.FollowUpItem(r.getId(), r.getPatientId(), r.getPatientName(), r.getPatientPhone(),
                r.getRecordNumber(), r.getProfessionalId(), r.getProfessionalName(), r.getTypeId(), r.getTypeName(),
                r.getDueOn(), ChronoUnit.DAYS.between(hoy, r.getDueOn()), r.getReminderStatus(), r.getReminderSentAt(),
                r.getFeeling() != null,
                r.getCaseId(),
                clinico ? r.getCaseTitle() : null,
                clinico ? r.getNotes() : null,
                clinico ? r.getFeeling() : null,
                clinico ? r.getFeelingAt() : null,
                clinico ? r.getFeelingNote() : null);
    }

    /** Mismo número si coinciden los últimos 8 dígitos (con o sin 502, guiones o espacios). */
    static boolean mismoTelefono(@Nullable String a, @Nullable String b) {
        String x = a == null ? "" : a.replaceAll("\\D", "");
        String y = b == null ? "" : b.replaceAll("\\D", "");
        return x.length() >= 8 && y.length() >= 8 && x.substring(x.length() - 8).equals(y.substring(y.length() - 8));
    }
}
