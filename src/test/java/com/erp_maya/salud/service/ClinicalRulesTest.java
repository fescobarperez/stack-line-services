package com.erp_maya.salud.service;

import com.erp_maya.salud.dto.ClinicalDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de la atención clínica: signos, diagnósticos, receta, firma y seguimiento. */
class ClinicalRulesTest {

    private static ClinicalDtos.Vitals signos(Integer sis, Integer dia, String temp, Integer dolor) {
        return new ClinicalDtos.Vitals(sis, dia, 80, 16, temp == null ? null : new BigDecimal(temp), 98,
                new BigDecimal("70"), new BigDecimal("170"), dolor);
    }

    @Test
    void losSignosVitalesFueraDeRangoSeRechazanConElCampo() {
        assertDoesNotThrow(() -> ClinicalRules.checkVitals(signos(120, 80, "36.8", 3)));
        assertDoesNotThrow(() -> ClinicalRules.checkVitals(null));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ClinicalRules.checkVitals(signos(1200, 80, "36.8", 3)));
        assertTrue(e.getMessage().contains("sistólica"));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.checkVitals(signos(120, 130, "36.8", 3)), "diastólica mayor");
        assertThrows(IllegalStateException.class, () -> ClinicalRules.checkVitals(signos(120, 80, "368", 3)), "temperatura");
        assertThrows(IllegalStateException.class, () -> ClinicalRules.checkVitals(signos(120, 80, "36.8", 11)), "dolor");
    }

    @Test
    void elImcSeCalculaConPesoYTalla() {
        assertEquals(new BigDecimal("24.2"), ClinicalRules.bmi(new BigDecimal("70"), new BigDecimal("170")));
        assertNull(ClinicalRules.bmi(null, new BigDecimal("170")));
        assertNull(ClinicalRules.bmi(new BigDecimal("70"), null));
    }

    @Test
    void losDiagnosticosQuedanConUnSoloPrincipalYCodigoNormalizado() {
        List<ClinicalDtos.DiagnosisData> dx = ClinicalRules.diagnoses(List.of(
                new ClinicalDtos.DiagnosisData("g43.9", "Migraña", null),
                new ClinicalDtos.DiagnosisData(null, "Gastritis", null),
                new ClinicalDtos.DiagnosisData("G43.9", "Migraña repetida", null)));
        assertEquals(2, dx.size(), "sin repetidos");
        assertEquals("G43.9", dx.get(0).code());
        assertTrue(dx.get(0).primary(), "sin marcar, el primero es el principal");
        assertFalse(dx.get(1).primary());

        List<ClinicalDtos.DiagnosisData> marcado = ClinicalRules.diagnoses(List.of(
                new ClinicalDtos.DiagnosisData(null, "A", false),
                new ClinicalDtos.DiagnosisData(null, "B", true)));
        assertFalse(marcado.get(0).primary());
        assertTrue(marcado.get(1).primary());

        assertThrows(IllegalStateException.class, () -> ClinicalRules.diagnoses(List.of(
                new ClinicalDtos.DiagnosisData(null, "A", true), new ClinicalDtos.DiagnosisData(null, "B", true))));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.code("dolor"));
        assertEquals("J00", ClinicalRules.code(" j00 "));
        assertNull(ClinicalRules.code(" "));
    }

    @Test
    void laRecetaPideElMedicamentoYUnaDuracionRazonable() {
        List<ClinicalDtos.PrescriptionData> rx = ClinicalRules.prescriptions(List.of(
                new ClinicalDtos.PrescriptionData(" Ibuprofeno 400 mg ", "1 tableta", "oral", "cada 8 horas", 5, " ", null)));
        assertEquals("Ibuprofeno 400 mg", rx.get(0).medication());
        assertNull(rx.get(0).quantity());
        assertThrows(IllegalStateException.class, () -> ClinicalRules.prescriptions(List.of(
                new ClinicalDtos.PrescriptionData("X", null, null, null, 0, null, null))));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.prescriptions(List.of(
                new ClinicalDtos.PrescriptionData(" ", null, null, null, null, null, null))));
    }

    @Test
    void paraFirmarHaceFaltaMotivoDiagnosticoYCaso() {
        assertDoesNotThrow(() -> ClinicalRules.checkSignable("dolor de cabeza", null, 1, 5L, null));
        assertDoesNotThrow(() -> ClinicalRules.checkSignable("dolor de cabeza", "Migraña", 0, null, "Migraña"));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.checkSignable(" ", "x", 1, 5L, null), "sin motivo");
        assertThrows(IllegalStateException.class, () -> ClinicalRules.checkSignable("x", " ", 0, 5L, null), "sin diagnóstico");
        assertThrows(IllegalStateException.class, () -> ClinicalRules.checkSignable("x", null, 1, null, " "), "sin caso");
        assertThrows(IllegalStateException.class, () -> ClinicalRules.checkEditable(ClinicalRules.FIRMADA));
        assertDoesNotThrow(() -> ClinicalRules.checkEditable(ClinicalRules.BORRADOR));
    }

    @Test
    void elSeguimientoVaDeUnoA365Dias() {
        assertEquals(Integer.valueOf(15), ClinicalRules.followUpDays(15));
        assertNull(ClinicalRules.followUpDays(null));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.followUpDays(0));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.followUpDays(400));
        assertEquals(LocalDate.of(2026, 11, 16), ClinicalRules.dueOn(LocalDate.of(2026, 11, 1), 15));
        assertEquals("01/11/2026", ClinicalRules.since(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void laRespuestaYElCumplimientoSonDeUnaLista() {
        assertEquals("mejoro", ClinicalRules.response("mejoro"));
        assertNull(ClinicalRules.response(""));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.response("bien"));
        assertEquals("parcial", ClinicalRules.adherence("parcial"));
        assertThrows(IllegalStateException.class, () -> ClinicalRules.adherence("a veces"));
    }

    @Test
    void laBusquedaCie10IgnoraTildesYMayusculas() {
        assertEquals("cefalea tension", ClinicalRules.normalize("  Cefalea  TENSIÓN "));
        assertEquals("%dolor%cabeza%", ClinicalRules.likePattern("Dolor  cabeza"));
        assertEquals("%100%", ClinicalRules.likePattern("100%"));
    }

    @Test
    void laConsultaSeAbreConElPacienteEnSalaOAtendido() {
        assertTrue(ClinicalRules.CITA_ATENDIBLE.contains(AgendaRules.EN_SALA));
        assertTrue(ClinicalRules.CITA_ATENDIBLE.contains(AgendaRules.COMPLETADA));
        assertFalse(ClinicalRules.CITA_ATENDIBLE.contains(AgendaRules.CONFIRMADA));
        assertFalse(ClinicalRules.CITA_ATENDIBLE.contains(AgendaRules.CANCELADA));
    }
}
