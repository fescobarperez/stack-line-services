package com.erp_maya.platform.security;

import org.junit.jupiter.api.Test;

import static com.erp_maya.platform.security.PlatformAccess.Decision.ALLOW;
import static com.erp_maya.platform.security.PlatformAccess.Decision.FORBID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El token de un operador solo abre /api/platform/**; el de un usuario de
 * empresa (o el de servicio del agente) nunca abre la consola.
 */
class PlatformAccessTest {

    @Test
    void elLoginDeLaConsolaEsPublico() {
        assertEquals(ALLOW, PlatformAccess.decide("/api/platform/auth/login", null));
    }

    @Test
    void laConsolaSoloConTokenDePlataforma() {
        assertEquals(ALLOW, PlatformAccess.decide("/api/platform/companies", "platform"));
        assertEquals(FORBID, PlatformAccess.decide("/api/platform/companies", null));
        assertEquals(FORBID, PlatformAccess.decide("/api/platform/auth/me", "service"));
    }

    @Test
    void elErpNuncaConTokenDePlataforma() {
        assertEquals(FORBID, PlatformAccess.decide("/api/clients", "platform"));
        assertEquals(ALLOW, PlatformAccess.decide("/api/clients", null));
        assertEquals(ALLOW, PlatformAccess.decide("/api/quotes/1", "service"));
    }

    @Test
    void unPrefijoParecidoNoEsLaConsola() {
        assertEquals(FORBID, PlatformAccess.decide("/api/platformx", "platform"));
        assertEquals(ALLOW, PlatformAccess.decide("/api/platformx", null));
    }
}
