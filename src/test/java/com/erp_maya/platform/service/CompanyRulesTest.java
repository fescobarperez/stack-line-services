package com.erp_maya.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las mismas reglas que src/lib/companyStatus.js de la consola. */
class CompanyRulesTest {

    @Test
    void transicionesPermitidas() {
        assertTrue(CompanyRules.canTransition("trial", "active"));
        assertTrue(CompanyRules.canTransition("suspended", "active"));
        assertFalse(CompanyRules.canTransition("active", "trial"));
        assertFalse(CompanyRules.canTransition("cancelled", "active"));
        assertFalse(CompanyRules.canTransition("active", "active"));
        assertFalse(CompanyRules.canTransition("active", null));
    }

    @Test
    void suspenderOCancelarPideMotivo() {
        assertTrue(CompanyRules.needsReason("suspended"));
        assertTrue(CompanyRules.needsReason("cancelled"));
        assertFalse(CompanyRules.needsReason("active"));
    }

    @Test
    void soloOperanActivasYEnPrueba() {
        assertTrue(CompanyRules.operates("active"));
        assertTrue(CompanyRules.operates("trial"));
        assertFalse(CompanyRules.operates("suspended"));
        assertFalse(CompanyRules.operates(null));
    }

    @Test
    void codigoNormalizado() {
        assertEquals("FERRE-ANGEL", CompanyRules.normalizeCode(" Ferré-Ángel "));
        assertEquals("MADERAVIVA", CompanyRules.normalizeCode("madera viva"));
        assertTrue(CompanyRules.isValidCode("MV1"));
        assertFalse(CompanyRules.isValidCode("AB"));
        assertFalse(CompanyRules.isValidCode("-ABC"));
        assertEquals(20, CompanyRules.normalizeCode("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length());
    }

    @Test
    void nitYCorreo() {
        assertTrue(CompanyRules.isValidNit("1234567-8"));
        assertTrue(CompanyRules.isValidNit("4455667-k"));
        assertTrue(CompanyRules.isValidNit("cf"));
        assertFalse(CompanyRules.isValidNit("12-34"));
        assertTrue(CompanyRules.isValidEmail("ana@maderaviva.gt"));
        assertFalse(CompanyRules.isValidEmail("ana@maderaviva"));
    }
}
