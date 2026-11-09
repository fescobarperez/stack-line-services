package com.erp_maya.salud.service;

import com.erp_maya.salud.dto.ClinicalDtos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que lleva la receta impresa (sin generar el PDF). */
class RecetaPdfServiceTest {

    private static ClinicalDtos.EncounterView consulta(String instrucciones) {
        ClinicalDtos.VitalsView v = new ClinicalDtos.VitalsView(null, null, null, null, null, null, null, null, null, null);
        return new ClinicalDtos.EncounterView(7L, 15L, "Ana López & Hijos", "EXP-000015", 34, "F", 3L, null, null,
                4L, "Dra. Ruiz", 9L, "Migraña", null, "firmada", "dolor", null, null, null, instrucciones, v,
                null, null, null,
                List.of(new ClinicalDtos.DiagnosisView("R51", "Cefalea", false),
                        new ClinicalDtos.DiagnosisView("G43.9", "Migraña, no especificada", true)),
                List.of(new ClinicalDtos.PrescriptionView("Ibuprofeno 400 mg", "1 tableta", "oral", "cada 8 horas", 5, "15 tabletas", "Con alimentos"),
                        new ClinicalDtos.PrescriptionView("Omeprazol 20 mg", null, null, "cada 24 horas", 1, null, null)),
                List.of(), null, null, null,
                Instant.parse("2026-11-03T20:00:00Z"), Instant.parse("2026-11-04T02:30:00Z"), false, null, List.of(), List.of());
    }

    private static final RecetaPdfService.Clinica CLINICA = new RecetaPdfService.Clinica(
            "Clínica <Sonrisas>", "Zona 10", "2222-3333", "", "", "#8b1e3f");

    @Test
    void laRecetaLlevaPacienteDiagnosticoMedicamentosYFirma() {
        String html = RecetaPdfService.xhtml(consulta("Reposo 2 días\nTomar agua"), CLINICA, "Medicina general", "12345", null);
        assertTrue(html.contains("Ana López &amp; Hijos"), "el nombre va escapado");
        assertTrue(html.contains("Clínica &lt;Sonrisas&gt;"));
        assertTrue(html.contains("G43.9 — Migraña, no especificada"), "el diagnóstico principal");
        assertFalse(html.contains("R51 — Cefalea"));
        assertTrue(html.contains("Ibuprofeno 400 mg"));
        assertTrue(html.contains("1 tableta · vía oral · cada 8 horas · por 5 días"));
        assertTrue(html.contains("por 1 día"));
        assertTrue(html.contains("Reposo 2 días<br />Tomar agua"));
        assertTrue(html.contains("Medicina general · Colegiado 12345"));
        assertTrue(html.contains("Expediente EXP-000015 · 34 años"));
        assertFalse(html.contains("%s"), "no queda ningún hueco sin llenar");
    }

    @Test
    void laFechaVaEnHoraDeGuatemala() {
        // 02:30 UTC del 4 es el 3 de noviembre en Guatemala.
        assertEquals("3 de noviembre de 2026", RecetaPdfService.fechaLarga(Instant.parse("2026-11-04T02:30:00Z")));
        assertTrue(RecetaPdfService.xhtml(consulta(null), CLINICA, null, null, null).contains("3 de noviembre de 2026"));
    }

    @Test
    void sinIndicacionesNiColegiadoNoQuedanEtiquetasVacias() {
        String html = RecetaPdfService.xhtml(consulta(null), CLINICA, null, null, null);
        assertFalse(html.contains("INDICACIONES"));
        assertFalse(html.contains("Colegiado"));
    }
}
