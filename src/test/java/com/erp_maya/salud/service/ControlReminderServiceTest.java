package com.erp_maya.salud.service;

import com.erp_maya.salud.domain.ControlReminderCandidate;
import com.erp_maya.salud.domain.FollowUp;
import com.erp_maya.salud.domain.FollowUpRow;
import com.erp_maya.salud.dto.CajaDtos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recordatorio del control (lo que se manda y cómo queda) y la bandeja de recepción. */
class ControlReminderServiceTest {

    private static final LocalDate VENCE = LocalDate.of(2026, 11, 13);
    private static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");

    private static ControlReminderCandidate candidata() {
        return new ControlReminderCandidate() {
            @Override public Long getId() { return 9L; }
            @Override public Long getCompanyId() { return 7L; }
            @Override public Long getPatientId() { return 15L; }
            @Override public LocalDate getDueOn() { return VENCE; }
            @Override public Long getTypeId() { return 3L; }
            @Override public Long getProfessionalId() { return 21L; }
            @Override public String getPatientName() { return "Ana López"; }
            @Override public String getPatientPhone() { return "5555-1234"; }
            @Override public String getProfessionalName() { return "Dra. Pérez"; }
            @Override public String getTypeName() { return "Control"; }
            @Override public String getCompanyName() { return "Clínica Central"; }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void elAvisoLlevaLaFechaEscritaYNingunDatoClinico() {
        Map<String, Object> cuerpo = ControlReminderService.cuerpo(candidata());
        assertEquals("control-9", cuerpo.get("notification_id"));
        assertEquals(7L, cuerpo.get("tenant_id"));
        assertEquals("recordatorio_control", cuerpo.get("kind"));
        Map<String, Object> p = (Map<String, Object>) cuerpo.get("payload");
        assertEquals(9L, p.get("followUpId"));
        assertEquals("2026-11-13", p.get("dueOn"));
        assertEquals(AgendaRules.fechaLarga(Instant.parse("2026-11-13T18:00:00Z")), p.get("date"));
        assertEquals(21L, p.get("professionalId"));
        assertFalse(p.containsKey("caseTitle"));
        assertFalse(p.containsKey("notes"));
    }

    @Test
    void enviadoOFallidoTrasLosReintentos() {
        FollowUp f = new FollowUp();
        ControlReminderService.anotar(f, false, "409 sin plantilla", AHORA);
        assertEquals(1, f.getReminderAttempts());
        assertNull(f.getReminderStatus());
        assertEquals(AHORA.plus(AgendaRules.reminderBackoff(1)), f.getReminderNextAt());
        for (int i = 1; i < AgendaRules.RECORDATORIO_INTENTOS; i++) ControlReminderService.anotar(f, false, "x", AHORA);
        assertEquals("fallido", f.getReminderStatus());

        FollowUp g = new FollowUp();
        ControlReminderService.anotar(g, true, null, AHORA);
        assertEquals("enviado", g.getReminderStatus());
        assertEquals(AHORA, g.getReminderSentAt());
    }

    private static FollowUpRow fila() {
        return new FollowUpRow() {
            @Override public Long getId() { return 9L; }
            @Override public Long getPatientId() { return 15L; }
            @Override public Long getCaseId() { return 4L; }
            @Override public LocalDate getDueOn() { return VENCE; }
            @Override public String getPatientName() { return "Ana López"; }
            @Override public String getCaseTitle() { return "Lumbalgia"; }
            @Override public String getNotes() { return "Revisar movilidad"; }
            @Override public String getFeeling() { return "peor"; }
        };
    }

    @Test
    void recepcionNoVeElCasoNiLaRespuestaPeroSabeQueContesto() {
        CajaDtos.FollowUpItem r = FollowUpService.item(fila(), LocalDate.of(2026, 11, 15), false);
        assertEquals(-2, r.daysLeft());
        assertTrue(r.answered());
        assertNull(r.caseTitle());
        assertNull(r.notes());
        assertNull(r.feeling());

        CajaDtos.FollowUpItem m = FollowUpService.item(fila(), LocalDate.of(2026, 11, 10), true);
        assertEquals(3, m.daysLeft());
        assertEquals("Lumbalgia", m.caseTitle());
        assertEquals("peor", m.feeling());
    }

    @Test
    void mismoTelefonoConOSinCodigoDePais() {
        assertTrue(FollowUpService.mismoTelefono("5555-1234", "50255551234"));
        assertFalse(FollowUpService.mismoTelefono("5555-1234", "50255551235"));
        assertFalse(FollowUpService.mismoTelefono(null, "50255551234"));
    }
}
