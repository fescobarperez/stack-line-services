package com.erp_maya.salud.service;

import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.ReminderCandidate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Qué se le manda a agents-services y cómo queda la cita después de cada intento. */
class AppointmentReminderServiceTest {

    private static final Instant INICIO = OffsetDateTime.parse("2026-11-03T09:30:00-06:00").toInstant();
    private static final Instant AHORA = OffsetDateTime.parse("2026-11-02T09:00:00-06:00").toInstant();

    private static ReminderCandidate candidata() {
        return new ReminderCandidate() {
            @Override public Long getId() { return 41L; }
            @Override public Long getCompanyId() { return 7L; }
            @Override public Long getPatientId() { return 15L; }
            @Override public Instant getStartsAt() { return INICIO; }
            @Override public Instant getEndsAt() { return INICIO.plus(Duration.ofMinutes(30)); }
            @Override public String getStatus() { return "confirmada"; }
            @Override public String getPatientName() { return "Ana López"; }
            @Override public String getPatientPhone() { return "5555-1234"; }
            @Override public String getProfessionalName() { return "Dra. Pérez"; }
            @Override public String getTypeName() { return "Limpieza"; }
            @Override public String getCompanyName() { return "Clínica Sonrisas"; }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void elAvisoLlevaLaFechaYaArmadaEnHoraDeGuatemala() {
        Map<String, Object> cuerpo = AppointmentReminderService.cuerpo(candidata());
        assertEquals("cita-41-" + INICIO.getEpochSecond(), cuerpo.get("notification_id"));
        assertEquals(Long.valueOf(7), cuerpo.get("tenant_id"));
        assertEquals("recordatorio_cita", cuerpo.get("kind"));
        Map<String, Object> p = (Map<String, Object>) cuerpo.get("payload");
        assertEquals(Long.valueOf(41), p.get("appointmentId"));
        assertEquals("5555-1234", p.get("phone"));
        assertEquals("martes 3 de noviembre", p.get("date"));
        assertEquals("09:30", p.get("time"));
        assertEquals("Clínica Sonrisas", p.get("companyName"));
        assertNull(p.get("resourceName"));
    }

    @Test
    void entregadoQuedaEnviado() {
        Appointment a = new Appointment();
        a.setReminderError("falló antes");
        AppointmentReminderService.anotar(a, true, null, AHORA);
        assertEquals("enviado", a.getReminderStatus());
        assertEquals(AHORA, a.getReminderSentAt());
        assertEquals(1, a.getReminderAttempts());
        assertNull(a.getReminderError());
        assertNull(a.getReminderNextAt());
    }

    @Test
    void unFalloReintentaMasTardeYAlQuintoSeRinde() {
        Appointment a = new Appointment();
        AppointmentReminderService.anotar(a, false, "409 fuera de la ventana", AHORA);
        assertNull(a.getReminderStatus());
        assertEquals(AHORA.plus(Duration.ofMinutes(5)), a.getReminderNextAt());
        assertEquals("409 fuera de la ventana", a.getReminderError());

        for (int i = 0; i < 3; i++) AppointmentReminderService.anotar(a, false, "x", AHORA);
        assertNull(a.getReminderStatus());
        assertEquals(AHORA.plus(Duration.ofMinutes(40)), a.getReminderNextAt());

        AppointmentReminderService.anotar(a, false, "x".repeat(800), AHORA);
        assertEquals("fallido", a.getReminderStatus());
        assertEquals(5, a.getReminderAttempts());
        assertNull(a.getReminderNextAt());
        assertEquals(500, a.getReminderError().length());
    }
}
