package com.erp_maya.salud.service;

import com.erp_maya.salud.dto.AgendaDtos;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas puras de la agenda: estados, horas, horarios y disponibilidad. */
class AgendaRulesTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    /** Hora de Guatemala del lunes de prueba. */
    private static Instant gt(String hhmm) {
        return OffsetDateTime.parse("2026-11-02T" + hhmm + ":00-06:00").toInstant();
    }

    private static AgendaRules.Window ventana(String de, String a, Long recurso) {
        return new AgendaRules.Window(LocalTime.parse(de), LocalTime.parse(a), recurso);
    }

    private static List<String> horas(List<AgendaDtos.Slot> slots) {
        return slots.stream().map(s -> AgendaRules.hora(s.startsAt())).toList();
    }

    // ── Estados ──────────────────────────────────────────────────────────

    @Test
    void elFlujoNormalDeUnaCita() {
        assertDoesNotThrow(() -> AgendaRules.checkTransition("solicitada", "confirmada"));
        assertDoesNotThrow(() -> AgendaRules.checkTransition("confirmada", "en_sala"));
        assertDoesNotThrow(() -> AgendaRules.checkTransition("en_sala", "en_atencion"));
        assertDoesNotThrow(() -> AgendaRules.checkTransition("en_atencion", "completada"));
        assertDoesNotThrow(() -> AgendaRules.checkTransition("solicitada", "en_sala"), "llegar confirma");
    }

    @Test
    void unaCitaCerradaNoCambiaYNoSeSaltaLaAtencion() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AgendaRules.checkTransition("completada", "cancelada"));
        assertTrue(e.getMessage().contains("ya está cerrada"));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkTransition("confirmada", "completada"));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkTransition("en_atencion", "no_asistio"));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkTransition("solicitada", "inventado"));
    }

    @Test
    void laLlegadaSoloElDiaDeLaCitaYNoAsistioSoloDespuesDeLaHora() {
        Instant cita = gt("10:00");
        assertDoesNotThrow(() -> AgendaRules.checkTiming("en_sala", cita, gt("09:40")));
        assertThrows(IllegalStateException.class,
                () -> AgendaRules.checkTiming("en_sala", cita, gt("09:40").minusSeconds(86400)));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkTiming("no_asistio", cita, gt("09:59")));
        assertDoesNotThrow(() -> AgendaRules.checkTiming("no_asistio", cita, gt("10:16")));
    }

    @Test
    void soloSeReprogramaLoQueNoHaEmpezado() {
        assertDoesNotThrow(() -> AgendaRules.checkReschedulable("confirmada"));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkReschedulable("en_sala"));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkReschedulable("cancelada"));
    }

    // ── Horas ────────────────────────────────────────────────────────────

    @Test
    void elFinSaleDelTipoDeCitaYSeValida() {
        assertEquals(gt("10:30"), AgendaRules.end(gt("10:00"), null, 30));
        assertEquals(gt("10:45"), AgendaRules.end(gt("10:00"), gt("10:45"), 30));
        assertThrows(IllegalStateException.class, () -> AgendaRules.end(gt("10:00"), gt("09:00"), 30));
        assertThrows(IllegalStateException.class, () -> AgendaRules.end(gt("08:00"), gt("17:00"), 30));
    }

    @Test
    void noSeAgendaEnElPasadoSalvoLaTolerancia() {
        assertDoesNotThrow(() -> AgendaRules.checkNotPast(gt("09:30"), gt("10:00"), AgendaRules.TOLERANCIA_PASADO));
        assertThrows(IllegalStateException.class,
                () -> AgendaRules.checkNotPast(gt("08:30"), gt("10:00"), AgendaRules.TOLERANCIA_PASADO));
    }

    @Test
    void citasContiguasNoSeCruzan() {
        assertFalse(AgendaRules.overlaps(gt("09:00"), gt("09:30"), gt("09:30"), gt("10:00")));
        assertTrue(AgendaRules.overlaps(gt("09:00"), gt("09:31"), gt("09:30"), gt("10:00")));
    }

    @Test
    void elDiaEsElDeGuatemala() {
        Instant[] dia = AgendaRules.day(LUNES);
        assertEquals(Instant.parse("2026-11-02T06:00:00Z"), dia[0]);
        assertEquals(Instant.parse("2026-11-03T06:00:00Z"), dia[1]);
    }

    // ── Configuración ────────────────────────────────────────────────────

    @Test
    void colorDuracionYHorario() {
        assertEquals("#2E7D32", AgendaRules.color("#2e7d32"));
        assertNull(AgendaRules.color(" "));
        assertThrows(IllegalStateException.class, () -> AgendaRules.color("verde"));
        assertEquals(Integer.valueOf(30), Integer.valueOf(AgendaRules.duration(30)));
        assertThrows(IllegalStateException.class, () -> AgendaRules.duration(3));
        assertThrows(IllegalStateException.class, () -> AgendaRules.duration(null));

        AgendaDtos.ScheduleSlot manana = new AgendaDtos.ScheduleSlot(1, LocalTime.of(8, 0), LocalTime.of(12, 0), null);
        AgendaDtos.ScheduleSlot tarde = new AgendaDtos.ScheduleSlot(1, LocalTime.of(14, 0), LocalTime.of(18, 0), null);
        AgendaDtos.ScheduleSlot cruzada = new AgendaDtos.ScheduleSlot(1, LocalTime.of(11, 0), LocalTime.of(15, 0), null);
        assertDoesNotThrow(() -> AgendaRules.checkSchedule(List.of(tarde, manana)));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkSchedule(List.of(manana, cruzada)));
        assertThrows(IllegalStateException.class, () -> AgendaRules.checkSchedule(List.of(
                new AgendaDtos.ScheduleSlot(8, LocalTime.of(8, 0), LocalTime.of(9, 0), null))));
    }

    // ── Disponibilidad ───────────────────────────────────────────────────

    @Test
    void ofreceLosHuecosLibresDeLaFranja() {
        List<AgendaDtos.Slot> s = AgendaRules.slots(LUNES, List.of(ventana("08:00", "10:00", null)),
                List.of(new AgendaRules.Busy(gt("08:30"), gt("09:00"))), Map.of(), List.of(),
                false, 30, 30, gt("07:00"));
        assertEquals(List.of("08:00", "09:00", "09:30"), horas(s));
        assertEquals(gt("09:30"), s.get(1).endsAt());
    }

    @Test
    void laCitaTieneQueCaberCompletaYNadaEnElPasado() {
        List<AgendaDtos.Slot> s = AgendaRules.slots(LUNES, List.of(ventana("08:00", "09:00", null)),
                List.of(), Map.of(), List.of(), false, 45, 15, gt("08:10"));
        assertEquals(List.of("08:15"), horas(s));
    }

    @Test
    void conRecursoFijoSeRespetaSuOcupacion() {
        List<AgendaDtos.Slot> s = AgendaRules.slots(LUNES, List.of(ventana("08:00", "09:00", 7L)),
                List.of(), Map.of(7L, List.of(new AgendaRules.Busy(gt("08:00"), gt("08:30")))), List.of(7L, 8L),
                false, 30, 30, gt("07:00"));
        assertEquals(List.of("08:30"), horas(s));
        assertEquals(Long.valueOf(7), s.get(0).resourceId());
    }

    @Test
    void siElTipoPideRecursoSeAsignaElPrimeroLibre() {
        Map<Long, List<AgendaRules.Busy>> ocupados = Map.of(
                7L, List.of(new AgendaRules.Busy(gt("08:00"), gt("09:00"))),
                8L, List.of(new AgendaRules.Busy(gt("08:30"), gt("09:00"))));
        List<AgendaDtos.Slot> s = AgendaRules.slots(LUNES, List.of(ventana("08:00", "09:00", null)),
                List.of(), ocupados, List.of(7L, 8L), true, 30, 30, gt("07:00"));
        assertEquals(List.of("08:00"), horas(s));
        assertEquals(Long.valueOf(8), s.get(0).resourceId());
    }

    @Test
    void sinRecursosLibresNoHayHueco() {
        List<AgendaDtos.Slot> s = AgendaRules.slots(LUNES, List.of(ventana("08:00", "09:00", null)),
                List.of(), Map.of(), List.of(), true, 30, 30, gt("07:00"));
        assertTrue(s.isEmpty());
    }

    // ── Asistente ────────────────────────────────────────────────────────

    @Test
    void elAsistenteSoloAgendaDentroDelHorario() {
        List<AgendaRules.Window> horario = List.of(
                new AgendaRules.Window(LocalTime.of(8, 0), LocalTime.of(12, 0), null),
                new AgendaRules.Window(LocalTime.of(14, 0), LocalTime.of(18, 0), null));
        assertTrue(AgendaRules.withinSchedule(horario, gt("08:00"), gt("08:30")));
        assertTrue(AgendaRules.withinSchedule(horario, gt("11:30"), gt("12:00")));
        assertFalse(AgendaRules.withinSchedule(horario, gt("11:45"), gt("12:15")), "se sale de la franja");
        assertFalse(AgendaRules.withinSchedule(horario, gt("12:30"), gt("13:00")), "hora de almuerzo");
        assertFalse(AgendaRules.withinSchedule(horario, gt("07:30"), gt("08:30")));
        assertFalse(AgendaRules.withinSchedule(List.of(), gt("09:00"), gt("09:30")), "ese día no atiende");
    }

    @Test
    void elAsistenteSoloConfirmaOCancela() {
        assertEquals(java.util.Set.of(AgendaRules.CONFIRMADA, AgendaRules.CANCELADA), AgendaRules.CAMBIOS_DEL_ASISTENTE);
    }

    // ── Recordatorios ────────────────────────────────────────────────────

    @Test
    void lasHorasDelRecordatorioVanDe2A72() {
        assertEquals(2, AgendaRules.reminderHours(2));
        assertEquals(24, AgendaRules.reminderHours(24));
        assertEquals(72, AgendaRules.reminderHours(72));
        assertThrows(IllegalStateException.class, () -> AgendaRules.reminderHours(1));
        assertThrows(IllegalStateException.class, () -> AgendaRules.reminderHours(73));
        assertThrows(IllegalStateException.class, () -> AgendaRules.reminderHours(null));
    }

    @Test
    void losReintentosEsperanCadaVezMas() {
        assertEquals(java.time.Duration.ofMinutes(5), AgendaRules.reminderBackoff(1));
        assertEquals(java.time.Duration.ofMinutes(10), AgendaRules.reminderBackoff(2));
        assertEquals(java.time.Duration.ofMinutes(20), AgendaRules.reminderBackoff(3));
        assertEquals(java.time.Duration.ofMinutes(40), AgendaRules.reminderBackoff(4));
        assertEquals(java.time.Duration.ofMinutes(320), AgendaRules.reminderBackoff(50), "con tope");
    }

    @Test
    void laFechaDelMensajeVaEnHoraDeGuatemala() {
        assertEquals("lunes 2 de noviembre", AgendaRules.fechaLarga(gt("09:00")));
        // 23:30 en Guatemala ya es martes en UTC: debe seguir diciendo lunes.
        assertEquals("lunes 2 de noviembre", AgendaRules.fechaLarga(gt("23:30")));
        assertEquals("09:00", AgendaRules.hora(gt("09:00")));
    }

    // ── Base de datos ────────────────────────────────────────────────────

    @Test
    void reconoceElChoqueConLaRestriccionDeNoSolape() {
        SQLException sql = new SQLException("conflicting key value", "23P01");
        assertTrue(AgendaRules.isOverlapViolation(new RuntimeException("envoltura", sql)));
        assertTrue(AgendaRules.isOverlapViolation(new RuntimeException("violates exclusion constraint \"ex_salud_appt_resource\"")));
        assertFalse(AgendaRules.isOverlapViolation(new RuntimeException("otro error", new SQLException("x", "23505"))));
        assertFalse(AgendaRules.isOverlapViolation(null));
    }
}
