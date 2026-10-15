package com.erp_maya.platform.security;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;

import java.util.Arrays;

/**
 * Quién puede entrar a qué. Los tokens llevan el atributo {@code kind}:
 * {@code platform} (operador de la consola), {@code service} (agents-services)
 * o ninguno (usuario de una empresa).
 *
 * <ul>
 *   <li>{@code /api/platform/**}: solo tokens de plataforma (salvo el login).</li>
 *   <li>El resto de {@code /api/**}: nunca un token de plataforma; un operador no
 *       es usuario de ninguna empresa y no tiene {@code companyId}.</li>
 * </ul>
 */
public final class PlatformAccess {

    public static final String KIND = "kind";
    public static final String KIND_PLATFORM = "platform";
    public static final String PREFIX = "/api/platform/";
    public static final String LOGIN = "/api/platform/auth/login";

    private PlatformAccess() {}

    public enum Decision { ALLOW, FORBID }

    /** Decide sin mirar la firma (eso ya lo hizo la seguridad de Micronaut). */
    public static Decision decide(String path, @Nullable String kind) {
        boolean plataforma = KIND_PLATFORM.equals(kind);
        if (path.startsWith(PREFIX)) {
            return path.equals(LOGIN) || plataforma ? Decision.ALLOW : Decision.FORBID;
        }
        return plataforma ? Decision.FORBID : Decision.ALLOW;
    }

    public static @Nullable String kind(@Nullable Authentication auth) {
        Object k = auth == null ? null : auth.getAttributes().get(KIND);
        return k == null ? null : k.toString();
    }

    public static Long operatorId(Authentication auth) {
        return Long.valueOf(auth.getAttributes().get("operatorId").toString());
    }

    public static String role(Authentication auth) {
        Object r = auth.getAttributes().get("role");
        return r == null ? "" : r.toString();
    }

    /** Lanza 403 si el rol del operador no está entre los permitidos. */
    public static void require(Authentication auth, String... roles) {
        String rol = role(auth);
        if (Arrays.stream(roles).noneMatch(rol::equals)) {
            throw new PlatformForbiddenException("Tu rol no permite esta acción");
        }
    }
}
