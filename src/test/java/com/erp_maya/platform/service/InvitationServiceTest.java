package com.erp_maya.platform.service;

import com.erp_maya.platform.domain.UserInvitation;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enlaces de activación: token largo al azar, solo su hash en la base, un solo uso. */
class InvitationServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void elTokenEsLargoYNoSeRepite() {
        String a = InvitationService.newToken();
        assertEquals(43, a.length());
        assertNotEquals(a, InvitationService.newToken());
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void seGuardaElHashNoElToken() {
        String h = InvitationService.hash("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h);
        assertEquals(64, h.length());
    }

    @Test
    void sirveUnaVezYMientrasNoVenza() {
        UserInvitation inv = new UserInvitation();
        inv.setExpiresAt(AHORA.plusSeconds(60));
        assertTrue(inv.usable(AHORA));
        assertFalse(inv.usable(AHORA.plusSeconds(60)));
        inv.setUsedAt(AHORA);
        assertFalse(inv.usable(AHORA));
        UserInvitation revocada = new UserInvitation();
        revocada.setExpiresAt(AHORA.plusSeconds(60));
        revocada.setRevokedAt(AHORA);
        assertFalse(revocada.usable(AHORA));
    }

    @Test
    void contrasenaDeActivacion() {
        assertTrue(ActivationService.passwordOk("madera2026"));
        assertFalse(ActivationService.passwordOk("corta1"));
        assertFalse(ActivationService.passwordOk("solotextolargo"));
        assertFalse(ActivationService.passwordOk("12345678"));
        assertFalse(ActivationService.passwordOk(null));
    }
}
