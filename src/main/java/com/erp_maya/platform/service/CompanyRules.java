package com.erp_maya.platform.service;

import io.micronaut.core.annotation.Nullable;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reglas de empresa de la consola: las mismas de src/lib/companyStatus.js en
 * stack-line-gestion-ui (estados, transiciones, código, NIT, correo).
 */
public final class CompanyRules {

    public static final String TRIAL = "trial";
    public static final String ACTIVE = "active";
    public static final String SUSPENDED = "suspended";
    public static final String CANCELLED = "cancelled";
    public static final List<String> STATUSES = List.of(TRIAL, ACTIVE, SUSPENDED, CANCELLED);

    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            TRIAL, List.of(ACTIVE, SUSPENDED, CANCELLED),
            ACTIVE, List.of(SUSPENDED, CANCELLED),
            SUSPENDED, List.of(ACTIVE, CANCELLED),
            CANCELLED, List.of());

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9-]{1,18}[A-Z0-9]$");
    private static final Pattern NIT = Pattern.compile("^\\d{1,12}-?[\\dK]$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final List<String> CURRENCIES = List.of("GTQ", "USD");

    private CompanyRules() {}

    public static boolean canTransition(@Nullable String from, @Nullable String to) {
        return from != null && to != null && TRANSITIONS.getOrDefault(from, List.of()).contains(to);
    }

    /** Suspender o cancelar exige motivo: queda en el historial. */
    public static boolean needsReason(String to) {
        return SUSPENDED.equals(to) || CANCELLED.equals(to);
    }

    /** Las que pueden operar (login, agente). */
    public static boolean operates(@Nullable String status) {
        return ACTIVE.equals(status) || TRIAL.equals(status);
    }

    public static String normalizeCode(@Nullable String raw) {
        String s = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9-]+", "");
        return s.length() > 20 ? s.substring(0, 20) : s;
    }

    public static boolean isValidCode(@Nullable String code) {
        return code != null && CODE.matcher(code).matches();
    }

    public static String normalizeNit(@Nullable String nit) {
        return nit == null ? "" : nit.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValidNit(@Nullable String nit) {
        String v = normalizeNit(nit);
        return "CF".equals(v) || NIT.matcher(v).matches();
    }

    public static boolean isValidEmail(@Nullable String email) {
        return email != null && EMAIL.matcher(email.trim()).matches();
    }

    public static boolean isValidCurrency(@Nullable String c) {
        return c != null && CURRENCIES.contains(c);
    }
}
