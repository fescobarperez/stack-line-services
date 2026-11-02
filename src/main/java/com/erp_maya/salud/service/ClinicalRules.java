package com.erp_maya.salud.service;

import com.erp_maya.salud.dto.ClinicalDtos;
import io.micronaut.core.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas puras de la atención clínica: rangos de signos vitales, diagnósticos,
 * receta, firma y seguimiento. Sin base ni contexto, para probarlas solas.
 */
public final class ClinicalRules {

    private ClinicalRules() {}

    public static final String BORRADOR = "borrador";
    public static final String FIRMADA = "firmada";
    public static final String CASO_ABIERTO = "abierto";
    public static final String CASO_CERRADO = "cerrado";

    public static final String FU_PENDIENTE = "pendiente";
    public static final String FU_AGENDADO = "agendado";
    public static final String FU_CUMPLIDO = "cumplido";
    public static final String FU_CANCELADO = "cancelado";

    public static final Set<String> RESPUESTAS = Set.of("mejoro", "igual", "empeoro");
    public static final Set<String> ADHERENCIAS = Set.of("completa", "parcial", "no");

    /** Citas en las que se puede abrir la consulta: el paciente llegó, está en atención o ya se atendió. */
    public static final Set<String> CITA_ATENDIBLE = Set.of(AgendaRules.EN_SALA, AgendaRules.EN_ATENCION, AgendaRules.COMPLETADA);

    public static final int MAX_DIAGNOSTICOS = 10;
    public static final int MAX_RECETA = 20;
    public static final int MAX_DIAS_SEGUIMIENTO = 365;
    public static final int MAX_CIE10 = 20;

    private static final Pattern CIE10 = Pattern.compile("^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,2})?$");

    // ── Signos vitales ───────────────────────────────────────────────────

    /** Fuera de rango es casi siempre un dedazo (120 en vez de 12): se rechaza con el campo y el rango. */
    public static void checkVitals(@Nullable ClinicalDtos.Vitals v) {
        if (v == null) return;
        rango(v.bpSystolic(), 50, 260, "La presión sistólica");
        rango(v.bpDiastolic(), 30, 160, "La presión diastólica");
        if (v.bpSystolic() != null && v.bpDiastolic() != null && v.bpDiastolic() >= v.bpSystolic()) {
            throw new IllegalStateException("La presión diastólica debe ser menor que la sistólica.");
        }
        rango(v.heartRate(), 20, 250, "La frecuencia cardiaca");
        rango(v.respRate(), 5, 80, "La frecuencia respiratoria");
        rango(v.temperature(), new BigDecimal("30"), new BigDecimal("45"), "La temperatura");
        rango(v.spo2(), 50, 100, "La saturación de oxígeno");
        rango(v.weightKg(), new BigDecimal("0.3"), new BigDecimal("400"), "El peso");
        rango(v.heightCm(), new BigDecimal("25"), new BigDecimal("250"), "La talla");
        rango(v.painScore(), 0, 10, "La escala de dolor");
    }

    /** IMC = peso / talla² (talla en metros), a un decimal; null si falta alguno. */
    public static @Nullable BigDecimal bmi(@Nullable BigDecimal weightKg, @Nullable BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) return null;
        BigDecimal m = heightCm.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        return weightKg.divide(m.multiply(m), 1, RoundingMode.HALF_UP);
    }

    private static void rango(@Nullable Integer v, int min, int max, String que) {
        if (v != null && (v < min || v > max)) {
            throw new IllegalStateException(que + " debe estar entre " + min + " y " + max + ".");
        }
    }

    private static void rango(@Nullable BigDecimal v, BigDecimal min, BigDecimal max, String que) {
        if (v != null && (v.compareTo(min) < 0 || v.compareTo(max) > 0)) {
            throw new IllegalStateException(que + " debe estar entre " + min.toPlainString() + " y " + max.toPlainString() + ".");
        }
    }

    // ── Diagnósticos y receta ────────────────────────────────────────────

    /**
     * Diagnósticos limpios: código en mayúsculas y con formato CIE-10, sin
     * repetidos, y exactamente uno principal (el marcado o, si ninguno, el
     * primero).
     */
    public static List<ClinicalDtos.DiagnosisData> diagnoses(@Nullable List<ClinicalDtos.DiagnosisData> in) {
        if (in == null || in.isEmpty()) return List.of();
        if (in.size() > MAX_DIAGNOSTICOS) throw new IllegalStateException("Como máximo " + MAX_DIAGNOSTICOS + " diagnósticos por consulta.");
        long principales = in.stream().filter(d -> Boolean.TRUE.equals(d.primary())).count();
        if (principales > 1) throw new IllegalStateException("Solo un diagnóstico puede ser el principal.");
        List<ClinicalDtos.DiagnosisData> out = new ArrayList<>();
        List<String> vistos = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            ClinicalDtos.DiagnosisData d = in.get(i);
            String desc = blank(d.description());
            if (desc == null) throw new IllegalStateException("Cada diagnóstico necesita su descripción.");
            String code = code(d.code());
            String llave = code != null ? code : desc.toLowerCase(Locale.ROOT);
            if (vistos.contains(llave)) continue;
            vistos.add(llave);
            boolean principal = principales == 0 ? out.isEmpty() : Boolean.TRUE.equals(d.primary());
            out.add(new ClinicalDtos.DiagnosisData(code, desc, principal));
        }
        return out;
    }

    /** Código CIE-10 normalizado («g43.9» → «G43.9»); null si viene vacío; error si no tiene el formato. */
    public static @Nullable String code(@Nullable String c) {
        String v = blank(c);
        if (v == null) return null;
        v = v.toUpperCase(Locale.ROOT).replace(" ", "");
        if (!CIE10.matcher(v).matches()) {
            throw new IllegalStateException("«" + c + "» no es un código CIE-10 (ejemplo: J06.9).");
        }
        return v;
    }

    public static List<ClinicalDtos.PrescriptionData> prescriptions(@Nullable List<ClinicalDtos.PrescriptionData> in) {
        if (in == null || in.isEmpty()) return List.of();
        if (in.size() > MAX_RECETA) throw new IllegalStateException("Como máximo " + MAX_RECETA + " medicamentos por receta.");
        List<ClinicalDtos.PrescriptionData> out = new ArrayList<>();
        for (ClinicalDtos.PrescriptionData p : in) {
            String med = blank(p.medication());
            if (med == null) throw new IllegalStateException("Cada línea de la receta necesita el medicamento.");
            if (p.durationDays() != null && (p.durationDays() < 1 || p.durationDays() > 365)) {
                throw new IllegalStateException("La duración del tratamiento va de 1 a 365 días.");
            }
            out.add(new ClinicalDtos.PrescriptionData(med, blank(p.dose()), blank(p.route()), blank(p.frequency()),
                    p.durationDays(), blank(p.quantity()), blank(p.instructions())));
        }
        return out;
    }

    public static @Nullable String response(@Nullable String r) {
        return oneOf(r, RESPUESTAS, "La respuesta al tratamiento");
    }

    public static @Nullable String adherence(@Nullable String a) {
        return oneOf(a, ADHERENCIAS, "El cumplimiento del tratamiento");
    }

    public static @Nullable Integer followUpDays(@Nullable Integer dias) {
        if (dias == null) return null;
        if (dias < 1 || dias > MAX_DIAS_SEGUIMIENTO) {
            throw new IllegalStateException("El control va de 1 a " + MAX_DIAS_SEGUIMIENTO + " días después de la consulta.");
        }
        return dias;
    }

    // ── Firma ────────────────────────────────────────────────────────────

    /**
     * Lo mínimo para firmar: el motivo (subjetivo), un diagnóstico o el
     * análisis escrito, y a qué caso pertenece.
     */
    public static void checkSignable(@Nullable String subjective, @Nullable String assessment, int diagnoses,
                                     @Nullable Long caseId, @Nullable String newCaseTitle) {
        if (blank(subjective) == null) throw new IllegalStateException("Escribe el motivo de consulta (subjetivo) antes de firmar.");
        if (diagnoses == 0 && blank(assessment) == null) {
            throw new IllegalStateException("Agrega al menos un diagnóstico o escribe el análisis antes de firmar.");
        }
        if (caseId == null && blank(newCaseTitle) == null) {
            throw new IllegalStateException("Indica a qué caso pertenece la consulta o el nombre del caso nuevo.");
        }
    }

    public static void checkEditable(String status) {
        if (!BORRADOR.equals(status)) {
            throw new IllegalStateException("La consulta ya está firmada: las correcciones van como adenda.");
        }
    }

    /** Fecha límite del control: hoy (Guatemala) más los días indicados. */
    public static LocalDate dueOn(LocalDate hoy, int dias) {
        return hoy.plusDays(dias);
    }

    /** Lo que se guarda en «desde» del medicamento de uso actual: la fecha de la receta. */
    public static String since(LocalDate fecha) {
        return String.format("%02d/%02d/%d", fecha.getDayOfMonth(), fecha.getMonthValue(), fecha.getYear());
    }

    // ── Búsqueda CIE-10 ──────────────────────────────────────────────────

    /** Minúsculas y sin tildes, como la columna search_text. */
    public static String normalize(@Nullable String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.replaceAll("\\s+", " ").trim();
    }

    /** «dolor cabeza» → «%dolor%cabeza%»: las palabras en orden, con lo que sea entre ellas. */
    public static String likePattern(String q) {
        String n = normalize(q).replace("%", "").replace("_", "");
        return "%" + String.join("%", n.split(" ")) + "%";
    }

    // ── Utilidades ───────────────────────────────────────────────────────

    static @Nullable String blank(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static @Nullable String oneOf(@Nullable String v, Set<String> validos, String que) {
        String x = blank(v);
        if (x == null) return null;
        if (!validos.contains(x)) throw new IllegalStateException(que + " no es válido: " + x + ".");
        return x;
    }
}
