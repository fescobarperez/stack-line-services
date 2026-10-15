package com.erp_maya.security.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas del token de servicio que usa agents-services: la llave debe coincidir
 * exactamente (una llave vacía nunca sirve de comodín) y solo las empresas en
 * prueba o activas reciben token.
 */
class ServiceTokenServiceTest {

    @Test
    void laLlaveDebeCoincidirExactamente() {
        assertTrue(ServiceTokenService.llaveValida("k-123456789", "k-123456789"));
        assertFalse(ServiceTokenService.llaveValida("k-123456789", "k-12345678"));
        assertFalse(ServiceTokenService.llaveValida("k-123456789", "K-123456789"));
        assertFalse(ServiceTokenService.llaveValida("k-123456789", null));
    }

    @Test
    void sinLlaveConfiguradaNadieEntra() {
        assertFalse(ServiceTokenService.llaveValida("", ""));
        assertFalse(ServiceTokenService.llaveValida(null, null));
        assertFalse(ServiceTokenService.llaveValida("  ", "  "));
    }

    @Test
    void soloEmpresasActivasOEnPrueba() {
        assertTrue(ServiceTokenService.empresaHabilitada("active"));
        assertTrue(ServiceTokenService.empresaHabilitada("trial"));
        assertFalse(ServiceTokenService.empresaHabilitada("suspended"));
        assertFalse(ServiceTokenService.empresaHabilitada("cancelled"));
        assertFalse(ServiceTokenService.empresaHabilitada(null));
    }
}
