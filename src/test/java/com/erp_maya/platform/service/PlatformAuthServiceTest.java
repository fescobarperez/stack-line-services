package com.erp_maya.platform.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas del login de operadores: correo normalizado, bloqueo tras 5 fallos y primer dueño. */
class PlatformAuthServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void elCorreoSeComparaSinMayusculasNiEspacios() {
        assertEquals("ana@stackline.gt", PlatformAuthService.normalizeEmail("  Ana@StackLine.GT "));
        assertEquals("", PlatformAuthService.normalizeEmail(null));
    }

    @Test
    void alQuintoFalloSeBloquea15Minutos() {
        assertNull(PlatformAuthService.lockAfterFailure(4, AHORA));
        assertEquals(AHORA.plusSeconds(900), PlatformAuthService.lockAfterFailure(5, AHORA));
    }

    @Test
    void elBloqueoVence() {
        Instant hasta = AHORA.plusSeconds(900);
        assertTrue(PlatformAuthService.locked(hasta, AHORA));
        assertFalse(PlatformAuthService.locked(hasta, hasta));
        assertFalse(PlatformAuthService.locked(null, AHORA));
    }

    @Test
    void fueraDeDevElPrimerDuenoNecesitaUnaContrasenaLarga() {
        assertFalse(PlatformOwnerSeeder.passwordAcceptable("stackline", false));
        assertTrue(PlatformOwnerSeeder.passwordAcceptable("una-clave-larga-1", false));
        assertTrue(PlatformOwnerSeeder.passwordAcceptable("stackline", true));
        assertFalse(PlatformOwnerSeeder.passwordAcceptable("", true));
        assertFalse(PlatformOwnerSeeder.passwordAcceptable(null, false));
    }
}
