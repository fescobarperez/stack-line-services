package com.erp_maya.lead.service;

import com.erp_maya.lead.domain.Lead;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas puras del aviso de prospectos. */
class LeadServiceTest {

    @Test
    void normalizaElInteres() {
        assertEquals("Software a medida", LeadService.interes(" software_a_medida "));
        assertEquals("Stackline", LeadService.interes("STACKLINE"));
        assertEquals("ERP para ferretería", LeadService.interes("ERP para ferretería"));
        assertNull(LeadService.interes("  "));
    }

    @Test
    void elCorreoEscapaLoQueEscribioElVisitante() {
        Lead l = new Lead();
        l.setName("<script>alert(1)</script>");
        l.setPhone("5555-1234");
        l.setSource("tino_web");
        l.setWantsDemo(true);
        String html = LeadService.cuerpo(l, true);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("el chat de la página web"));
        assertTrue(html.contains("Quiere demo"));
        assertFalse(html.contains("Correo</td>"), "los campos vacíos no se muestran");
    }
}
