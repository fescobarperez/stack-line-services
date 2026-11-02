package com.erp_maya.salud.service;

import com.erp_maya.salud.dto.PatientDtos;
import io.micronaut.core.annotation.Nullable;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas puras del paciente: normalizan y validan sin tocar la base, para
 * poder probarlas sin levantar el servicio. Una regla violada lanza
 * IllegalStateException, que la API devuelve como 409 con el mensaje.
 */
public final class PatientRules {

    private PatientRules() {}

    public static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    public static final List<String> DOC_TYPES = List.of("DPI", "PASAPORTE", "OTRO");
    public static final List<String> SEXES = List.of("F", "M", "I");
    public static final List<String> STATUSES = List.of("activo", "inactivo", "fallecido");
    public static final List<String> CHANNELS = List.of("whatsapp", "sms", "email", "ninguno");
    public static final List<String> BLOOD_TYPES = List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
    public static final List<String> ALLERGY_KINDS = List.of("medicamento", "alimento", "latex", "ambiental", "otro");
    public static final List<String> SEVERITIES = List.of("leve", "moderada", "severa");
    public static final List<String> HISTORY_KINDS = List.of("cronica", "quirurgico", "hospitalizacion",
            "traumatico", "familiar", "gineco_obstetrico", "otro");
    public static final List<String> CONSENT_KINDS = List.of("datos", "tratamiento", "fotos", "comunicacion",
            "directivas", "otro");

    /** Mayoría de edad en Guatemala: un menor necesita un tutor legal registrado. */
    public static final int MAYORIA_DE_EDAD = 18;
    private static final int EDAD_MAXIMA = 130;
    private static final int DIGITOS_DPI = 13;
    private static final int DIGITOS_TELEFONO = 8;

    /** Texto recortado, o null si viene vacío. */
    public static @Nullable String blank(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Valor de una lista cerrada: vacío → null; si no está en la lista, error
     * con las opciones válidas. Compara sin distinguir mayúsculas y devuelve
     * el valor como está en la lista.
     */
    public static @Nullable String oneOf(@Nullable String value, List<String> allowed, String campo) {
        String v = blank(value);
        if (v == null) return null;
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) return a;
        }
        throw new IllegalStateException(campo + " no válido: «" + v + "». Usa " + String.join(", ", allowed) + ".");
    }

    /**
     * Documento de identidad normalizado. El DPI se guarda solo con sus 13
     * dígitos (se escribe con espacios o guiones) para que la búsqueda y la
     * regla de no duplicar funcionen sin importar cómo lo tecleó recepción.
     * Devuelve {tipo, número}, los dos null si no hay documento.
     */
    public static String[] document(@Nullable String type, @Nullable String number) {
        String t = oneOf(type, DOC_TYPES, "Tipo de documento");
        String n = blank(number);
        if (t == null && n == null) return new String[] {null, null};
        if (t == null) throw new IllegalStateException("Indica el tipo de documento (DPI, PASAPORTE u OTRO).");
        if (n == null) throw new IllegalStateException("Falta el número de " + t + ".");
        if ("DPI".equals(t)) {
            String digitos = n.replaceAll("[\\s-]", "");
            if (!digitos.matches("\\d{" + DIGITOS_DPI + "}")) {
                throw new IllegalStateException("El DPI debe tener 13 dígitos.");
            }
            return new String[] {t, digitos};
        }
        return new String[] {t, n.toUpperCase(Locale.ROOT)};
    }

    public static LocalDate today() {
        return LocalDate.now(GUATEMALA);
    }

    public static @Nullable Integer age(@Nullable LocalDate birth, LocalDate today) {
        return birth == null ? null : Period.between(birth, today).getYears();
    }

    public static void validateBirthDate(@Nullable LocalDate birth, LocalDate today) {
        if (birth == null) return;
        if (birth.isAfter(today)) {
            throw new IllegalStateException("La fecha de nacimiento no puede ser futura.");
        }
        if (birth.isBefore(today.minusYears(EDAD_MAXIMA))) {
            throw new IllegalStateException("Revisa la fecha de nacimiento: da más de " + EDAD_MAXIMA + " años.");
        }
    }

    /**
     * Responsables: nadie es su propio responsable, un cliente no se repite,
     * hay un solo pagador (si no, la caja no sabe a quién facturar) y un menor
     * de edad necesita al menos un tutor legal.
     */
    public static void validateGuardians(@Nullable List<PatientDtos.GuardianData> guardians,
                                         @Nullable Long patientId, @Nullable Integer age) {
        List<PatientDtos.GuardianData> list = guardians == null ? List.of() : guardians;
        Set<Long> vistos = new HashSet<>();
        int pagadores = 0;
        boolean tutor = false;
        for (PatientDtos.GuardianData g : list) {
            if (g.clientId().equals(patientId)) {
                throw new IllegalStateException("El paciente no puede ser su propio responsable.");
            }
            if (!vistos.add(g.clientId())) {
                throw new IllegalStateException("Un responsable aparece dos veces.");
            }
            if (!g.legalGuardian() && !g.payer()) {
                throw new IllegalStateException("Cada responsable debe ser tutor legal, quien paga, o ambos.");
            }
            if (g.payer()) pagadores++;
            if (g.legalGuardian()) tutor = true;
        }
        if (pagadores > 1) {
            throw new IllegalStateException("Solo puede haber un responsable que paga.");
        }
        if (age != null && age < MAYORIA_DE_EDAD && !tutor) {
            throw new IllegalStateException("El paciente es menor de edad: registra al menos un tutor legal.");
        }
    }

    /** Estado y fecha de fallecimiento: la fecha solo aplica a «fallecido» y no puede ser futura. */
    public static @Nullable LocalDate deceasedOn(String status, @Nullable LocalDate deceasedOn, LocalDate today) {
        if (!"fallecido".equals(status)) return null;
        if (deceasedOn != null && deceasedOn.isAfter(today)) {
            throw new IllegalStateException("La fecha de fallecimiento no puede ser futura.");
        }
        return deceasedOn;
    }

    /** Antecedente familiar sin parentesco no sirve: «diabetes» de quién. */
    public static void validateHistory(@Nullable List<PatientDtos.HistoryData> history) {
        if (history == null) return;
        for (PatientDtos.HistoryData h : history) {
            String kind = oneOf(h.kind(), HISTORY_KINDS, "Tipo de antecedente");
            if ("familiar".equals(kind) && blank(h.relative()) == null) {
                throw new IllegalStateException("Indica el parentesco del antecedente familiar «" + h.description().trim() + "».");
            }
        }
    }

    /** Texto de búsqueda sin comodines de LIKE, para que «%» no lo devuelva todo. */
    public static String searchText(@Nullable String q) {
        String t = blank(q);
        return t == null ? "" : t.replaceAll("[%_\\\\]", "");
    }

    /**
     * Últimos 8 dígitos si el texto parece un teléfono, o "" si no. Igual que
     * ClientService.findByPhone: el número llega con o sin código de país.
     */
    public static String phoneDigits(@Nullable String q) {
        String t = blank(q);
        if (t == null || !t.matches("[\\d\\s()+-]+")) return "";
        String d = t.replaceAll("\\D", "");
        return d.length() < DIGITOS_TELEFONO ? "" : d.substring(d.length() - DIGITOS_TELEFONO);
    }
}
