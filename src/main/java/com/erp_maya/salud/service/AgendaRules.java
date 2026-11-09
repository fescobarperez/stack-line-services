package com.erp_maya.salud.service;

import com.erp_maya.salud.dto.AgendaDtos;
import io.micronaut.core.annotation.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas puras de la agenda: estados de la cita, horarios, solapes y
 * disponibilidad. Sin base de datos, para probarlas sin levantar nada.
 * Una regla violada lanza IllegalStateException (la API responde 409).
 */
public final class AgendaRules {

    private AgendaRules() {}

    public static final ZoneId GUATEMALA = PatientRules.GUATEMALA;

    public static final String SOLICITADA = "solicitada";
    public static final String CONFIRMADA = "confirmada";
    public static final String EN_SALA = "en_sala";
    public static final String EN_ATENCION = "en_atencion";
    public static final String COMPLETADA = "completada";
    public static final String CANCELADA = "cancelada";
    public static final String NO_ASISTIO = "no_asistio";

    public static final List<String> STATUSES = List.of(SOLICITADA, CONFIRMADA, EN_SALA, EN_ATENCION,
            COMPLETADA, CANCELADA, NO_ASISTIO);
    public static final List<String> RESOURCE_KINDS = List.of("consultorio", "sillon", "equipo", "sala", "otro");

    /**
     * Qué estado puede seguir a cuál. Es el diagrama del plan:
     * Solicitada → Confirmada → En sala → En atención → Completada, con
     * cancelación y «no asistió» como salidas. Llegar sin haber confirmado
     * (Solicitada → En sala) vale: que llegue es la confirmación.
     */
    static final Map<String, List<String>> TRANSITIONS = Map.of(
            SOLICITADA, List.of(CONFIRMADA, EN_SALA, CANCELADA, NO_ASISTIO),
            CONFIRMADA, List.of(EN_SALA, CANCELADA, NO_ASISTIO),
            EN_SALA, List.of(EN_ATENCION, CANCELADA),
            EN_ATENCION, List.of(COMPLETADA),
            COMPLETADA, List.of(),
            CANCELADA, List.of(),
            NO_ASISTIO, List.of());

    /** Estados que ocupan el horario (los demás lo liberan). */
    public static final Set<String> OCUPAN = Set.of(SOLICITADA, CONFIRMADA, EN_SALA, EN_ATENCION, COMPLETADA);

    /** Duración máxima de una cita o de un tipo de cita, en minutos. */
    public static final int MAX_MINUTOS = 480;
    /** Al crear, cuánto en el pasado se acepta (la cita que se registra cuando el paciente ya llegó). */
    public static final Duration TOLERANCIA_PASADO = Duration.ofMinutes(60);

    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    // ── Estados ──────────────────────────────────────────────────────────

    public static List<String> next(String status) {
        return TRANSITIONS.getOrDefault(status, List.of());
    }

    public static void checkTransition(String from, String to) {
        if (!STATUSES.contains(to)) {
            throw new IllegalStateException("Estado no válido: «" + to + "».");
        }
        if (!next(from).contains(to)) {
            List<String> posibles = next(from);
            throw new IllegalStateException("Una cita «" + from + "» no puede pasar a «" + to + "»"
                    + (posibles.isEmpty() ? ": ya está cerrada." : ". Puede pasar a: " + String.join(", ", posibles) + "."));
        }
    }

    /**
     * Reglas de calendario de cada cambio: solo se marca la llegada o la
     * atención el día de la cita, y «no asistió» solo cuando ya pasó la hora.
     */
    public static void checkTiming(String to, Instant startsAt, Instant now) {
        LocalDate dia = startsAt.atZone(GUATEMALA).toLocalDate();
        LocalDate hoy = now.atZone(GUATEMALA).toLocalDate();
        if ((EN_SALA.equals(to) || EN_ATENCION.equals(to)) && !dia.equals(hoy)) {
            throw new IllegalStateException("Solo se marca la llegada o la atención el día de la cita (" + fecha(startsAt) + ").");
        }
        if (NO_ASISTIO.equals(to) && now.isBefore(startsAt)) {
            throw new IllegalStateException("Todavía no es la hora de la cita: «no asistió» se marca después de las "
                    + hora(startsAt) + ".");
        }
    }

    /** Solo se mueve una cita que todavía no empezó. */
    public static void checkReschedulable(String status) {
        if (!SOLICITADA.equals(status) && !CONFIRMADA.equals(status)) {
            throw new IllegalStateException("Solo se reprograma una cita solicitada o confirmada; esta está «" + status + "».");
        }
    }

    // ── Horas ────────────────────────────────────────────────────────────

    /** Fin de la cita: el que venga o la duración del tipo. Valida el rango. */
    public static Instant end(Instant startsAt, @Nullable Instant endsAt, int durationMinutes) {
        Instant fin = endsAt != null ? endsAt : startsAt.plus(Duration.ofMinutes(durationMinutes));
        if (!fin.isAfter(startsAt)) {
            throw new IllegalStateException("La cita debe terminar después de empezar.");
        }
        if (Duration.between(startsAt, fin).toMinutes() > MAX_MINUTOS) {
            throw new IllegalStateException("Una cita no puede durar más de " + (MAX_MINUTOS / 60) + " horas.");
        }
        return fin;
    }

    public static void checkNotPast(Instant startsAt, Instant now, Duration tolerancia) {
        if (startsAt.isBefore(now.minus(tolerancia))) {
            throw new IllegalStateException("No se puede agendar en el pasado (" + fecha(startsAt) + ").");
        }
    }

    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    public static String hora(Instant i) {
        return HORA.format(i.atZone(GUATEMALA));
    }

    public static String fecha(Instant i) {
        return FECHA_HORA.format(i.atZone(GUATEMALA));
    }

    /** Inicio y fin del día en Guatemala, como instantes. */
    public static Instant[] day(LocalDate date) {
        return new Instant[] {date.atStartOfDay(GUATEMALA).toInstant(), date.plusDays(1).atStartOfDay(GUATEMALA).toInstant()};
    }

    // ── Configuración ────────────────────────────────────────────────────

    public static @Nullable String color(@Nullable String c) {
        String v = PatientRules.blank(c);
        if (v == null) return null;
        if (!COLOR.matcher(v).matches()) {
            throw new IllegalStateException("El color debe ser #RRGGBB, por ejemplo #2E7D32.");
        }
        return v.toUpperCase();
    }

    public static int duration(@Nullable Integer minutes) {
        if (minutes == null || minutes < 5 || minutes > MAX_MINUTOS) {
            throw new IllegalStateException("La duración debe estar entre 5 y " + MAX_MINUTOS + " minutos.");
        }
        return minutes;
    }

    /** Horario semanal: días válidos, fin después del inicio y sin franjas cruzadas el mismo día. */
    public static void checkSchedule(List<AgendaDtos.ScheduleSlot> slots) {
        List<AgendaDtos.ScheduleSlot> orden = new ArrayList<>(slots);
        orden.sort(Comparator.comparing(AgendaDtos.ScheduleSlot::weekday).thenComparing(AgendaDtos.ScheduleSlot::start));
        AgendaDtos.ScheduleSlot anterior = null;
        for (AgendaDtos.ScheduleSlot s : orden) {
            if (s.weekday() < 1 || s.weekday() > 7) {
                throw new IllegalStateException("Día no válido: " + s.weekday() + " (1 = lunes … 7 = domingo).");
            }
            if (!s.end().isAfter(s.start())) {
                throw new IllegalStateException("Una franja del horario termina antes de empezar (" + s.start() + "–" + s.end() + ").");
            }
            if (anterior != null && anterior.weekday().equals(s.weekday()) && s.start().isBefore(anterior.end())) {
                throw new IllegalStateException("Dos franjas del mismo día se cruzan (" + anterior.start() + "–"
                        + anterior.end() + " y " + s.start() + "–" + s.end() + ").");
            }
            anterior = s;
        }
    }

    // ── Disponibilidad ───────────────────────────────────────────────────

    /** Franja de trabajo de un día: hora local y, si la tiene, su recurso fijo. */
    public record Window(LocalTime start, LocalTime end, @Nullable Long resourceId) {}

    /** Intervalo ocupado. */
    public record Busy(Instant start, Instant end) {}

    /**
     * Horarios libres de un profesional en un día.
     *
     * Recorre cada franja de trabajo en pasos de `step` minutos y ofrece los
     * inicios en los que cabe la cita completa sin cruzarse con lo que ya
     * ocupa al profesional (citas activas y bloqueos). Si el tipo de cita
     * pide recurso, además debe haber uno libre: el de la franja si la franja
     * tiene uno fijo, o el primero libre de `resources`. Nada en el pasado.
     */
    public static List<AgendaDtos.Slot> slots(LocalDate date, List<Window> windows, List<Busy> professionalBusy,
                                              Map<Long, List<Busy>> resourceBusy, List<Long> resources,
                                              boolean requiresResource, int durationMinutes, int stepMinutes,
                                              Instant now) {
        List<AgendaDtos.Slot> out = new ArrayList<>();
        Duration dur = Duration.ofMinutes(durationMinutes);
        Duration paso = Duration.ofMinutes(Math.max(5, stepMinutes));
        List<Window> orden = new ArrayList<>(windows);
        orden.sort(Comparator.comparing(Window::start));
        for (Window w : orden) {
            Instant fin = date.atTime(w.end()).atZone(GUATEMALA).toInstant();
            for (Instant s = date.atTime(w.start()).atZone(GUATEMALA).toInstant();
                 !s.plus(dur).isAfter(fin); s = s.plus(paso)) {
                Instant e = s.plus(dur);
                if (s.isBefore(now) || ocupado(professionalBusy, s, e)) continue;
                Long recurso = null;
                if (w.resourceId() != null) {
                    if (ocupado(resourceBusy.getOrDefault(w.resourceId(), List.of()), s, e)) continue;
                    recurso = w.resourceId();
                } else if (requiresResource) {
                    recurso = libre(resources, resourceBusy, s, e);
                    if (recurso == null) continue;
                }
                out.add(new AgendaDtos.Slot(s, e, recurso));
            }
        }
        return out;
    }

    private static boolean ocupado(List<Busy> busy, Instant s, Instant e) {
        for (Busy b : busy) {
            if (overlaps(s, e, b.start(), b.end())) return true;
        }
        return false;
    }

    private static @Nullable Long libre(List<Long> resources, Map<Long, List<Busy>> busy, Instant s, Instant e) {
        for (Long r : resources) {
            if (!ocupado(busy.getOrDefault(r, List.of()), s, e)) return r;
        }
        return null;
    }

    /**
     * ¿La cita cabe completa dentro de una franja de trabajo de ese día? El
     * asistente solo agenda dentro del horario; recepción puede agendar fuera
     * (horas extra) bajo su criterio.
     */
    public static boolean withinSchedule(List<Window> windows, Instant start, Instant end) {
        LocalDate dia = start.atZone(GUATEMALA).toLocalDate();
        if (!end.atZone(GUATEMALA).toLocalDate().equals(dia) && !end.equals(dia.plusDays(1).atStartOfDay(GUATEMALA).toInstant())) {
            return false;
        }
        for (Window w : windows) {
            Instant ini = dia.atTime(w.start()).atZone(GUATEMALA).toInstant();
            Instant fin = dia.atTime(w.end()).atZone(GUATEMALA).toInstant();
            if (!start.isBefore(ini) && !end.isAfter(fin)) return true;
        }
        return false;
    }

    /** Lo que el asistente puede hacerle a una cita: confirmarla o cancelarla (el resto es de la clínica). */
    public static final Set<String> CAMBIOS_DEL_ASISTENTE = Set.of(CONFIRMADA, CANCELADA);

    // ── Recordatorios ────────────────────────────────────────────────────

    public static final int RECORDATORIO_HORAS_MIN = 2;
    public static final int RECORDATORIO_HORAS_MAX = 72;
    public static final int RECORDATORIO_INTENTOS = 5;

    public static int reminderHours(@Nullable Integer horas) {
        if (horas == null || horas < RECORDATORIO_HORAS_MIN || horas > RECORDATORIO_HORAS_MAX) {
            throw new IllegalStateException("El recordatorio se manda entre " + RECORDATORIO_HORAS_MIN + " y "
                    + RECORDATORIO_HORAS_MAX + " horas antes de la cita.");
        }
        return horas;
    }

    /** Espera antes de reintentar un recordatorio: 5, 10, 20, 40 minutos… */
    public static Duration reminderBackoff(int intentos) {
        return Duration.ofMinutes(5L << Math.max(0, Math.min(intentos - 1, 6)));
    }

    private static final String[] DIAS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /** «martes 3 de noviembre»: la fecha como la dice una persona, en hora de Guatemala. */
    public static String fechaLarga(Instant i) {
        var z = i.atZone(GUATEMALA);
        return DIAS[z.getDayOfWeek().getValue() - 1] + " " + z.getDayOfMonth() + " de " + MESES[z.getMonthValue() - 1];
    }

    // ── Base de datos ────────────────────────────────────────────────────

    /**
     * ¿El error viene de la restricción de no solape (EXCLUDE, SQLSTATE
     * 23P01)? Pasa cuando dos personas reservan el mismo horario a la vez:
     * la verificación previa no lo vio porque la otra cita aún no existía.
     */
    public static boolean isOverlapViolation(@Nullable Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof SQLException sql && "23P01".equals(sql.getSQLState())) return true;
            String m = c.getMessage();
            if (m != null && (m.contains("ex_salud_appt_professional") || m.contains("ex_salud_appt_resource"))) return true;
        }
        return false;
    }
}
