package com.erp_maya.salud.service;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Permisos de Salud, validados en el servidor.
 *
 * El resto del ERP solo oculta el menú en la app; aquí no basta, porque el
 * expediente clínico es información sensible: si la pantalla lo esconde pero
 * la API lo entrega, cualquiera con un token lo lee. Por eso cada operación
 * pide su permiso explícitamente.
 *
 * Mismo formato que el resto: "clave|acción" en Role.permissions, o "*" para
 * todo (el Administrador). Claves:
 *   - `patients`: la ficha administrativa (lo que ve recepción).
 *   - `clinical_record`: alergias, antecedentes, medicamentos y demás datos
 *     clínicos, y la bitácora de quién abrió cada expediente.
 *   - `agenda`: citas y bloqueos.
 *   - `agenda_config`: profesionales, horarios, recursos y tipos de cita.
 *   - `salud_billing`: cobros de citas, paquetes y anticipos (lo que ve caja).
 *   - `salud_followup`: la bandeja de controles por agendar.
 *   - `salud_reports`: los reportes de la clínica.
 */
public final class SaludAccess {

    private SaludAccess() {}

    public static final String PATIENTS = "patients";
    public static final String CLINICAL = "clinical_record";
    /** La agenda: ver citas, agendar, mover y cambiar su estado. */
    public static final String AGENDA = "agenda";
    /** Configuración de la agenda: profesionales, horarios, recursos y tipos de cita. */
    public static final String AGENDA_CONFIG = "agenda_config";
    /** Caja de Salud: cobrar citas, vender paquetes, recibir anticipos y anular cobros. */
    public static final String BILLING = "salud_billing";
    /** Seguimiento: la bandeja de controles vencidos y por vencer. */
    public static final String FOLLOWUP = "salud_followup";
    /** Reportes de Salud: citas, inasistencia, ingresos por servicio, controles vencidos. */
    public static final String REPORTS = "salud_reports";

    public static final String VER = "ver";
    public static final String CREAR = "crear";
    public static final String EDITAR = "editar";

    /**
     * Quién hace la operación y qué puede hacer. `service` es el asistente
     * (TINO) con el token de servicio de la empresa: agenda con reglas más
     * estrictas que recepción (ver AppointmentService).
     */
    public record Actor(@Nullable Long userId, Set<String> permissions, boolean service) {

        public Actor(@Nullable Long userId, Set<String> permissions) {
            this(userId, permissions, false);
        }

        public boolean can(String key, String action) {
            return permite(permissions, key, action);
        }

        public void require(String key, String action) {
            if (!can(key, action)) {
                throw new SaludForbiddenException(mensaje(key, action));
            }
        }
    }

    /** Arma el actor desde el JWT: userId y permisos (atributo `permissions` o roles). */
    public static Actor of(@Nullable Authentication auth) {
        if (auth == null) {
            return new Actor(null, Set.of());
        }
        Set<String> perms = new HashSet<>(auth.getRoles());
        Object attr = auth.getAttributes().get("permissions");
        if (attr instanceof Collection<?> c) {
            c.forEach(p -> { if (p != null) perms.add(p.toString()); });
        }
        boolean servicio = "service".equals(String.valueOf(auth.getAttributes().get("kind")));
        return new Actor(toLong(auth.getAttributes().get("userId")), Set.copyOf(perms), servicio);
    }

    static boolean permite(Collection<String> perms, String key, String action) {
        return perms != null && (perms.contains("*") || perms.contains(key + "|" + action));
    }

    static String mensaje(String key, String action) {
        String que = switch (key) {
            case CLINICAL -> "el expediente clínico";
            case AGENDA -> "la agenda";
            case AGENDA_CONFIG -> "la configuración de la agenda";
            case BILLING -> "los cobros de Salud";
            case FOLLOWUP -> "el seguimiento de controles";
            case REPORTS -> "los reportes de Salud";
            default -> "los pacientes";
        };
        String verbo = switch (action) {
            case VER -> "ver";
            case CREAR -> "registrar";
            default -> "modificar";
        };
        return "Tu rol no tiene permiso para " + verbo + " " + que + ". Pídeselo al administrador.";
    }

    static @Nullable Long toLong(@Nullable Object v) {
        if (v == null) return null;
        try {
            return Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
