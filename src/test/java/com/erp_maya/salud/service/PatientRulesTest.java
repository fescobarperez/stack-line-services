package com.erp_maya.salud.service;

import com.erp_maya.salud.dto.PatientDtos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas puras del paciente. */
class PatientRulesTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    private static PatientDtos.GuardianData tutor(long clientId) {
        return new PatientDtos.GuardianData(clientId, "Madre", true, false);
    }

    private static PatientDtos.GuardianData pagador(long clientId) {
        return new PatientDtos.GuardianData(clientId, "Empresa", false, true);
    }

    @Test
    void elDpiSeGuardaSoloConSusDigitos() {
        assertArrayEquals(new String[] {"DPI", "1234567890101"}, PatientRules.document("dpi", "1234 56789 0101"));
        assertArrayEquals(new String[] {"DPI", "1234567890101"}, PatientRules.document("DPI", "1234-56789-0101"));
        assertArrayEquals(new String[] {"PASAPORTE", "AB12345"}, PatientRules.document("Pasaporte", " ab12345 "));
        assertArrayEquals(new String[] {null, null}, PatientRules.document(null, "  "));
    }

    @Test
    void unDpiIncompletoOSinTipoSeRechaza() {
        assertThrows(IllegalStateException.class, () -> PatientRules.document("DPI", "12345"));
        assertThrows(IllegalStateException.class, () -> PatientRules.document("DPI", "12345678901AB"));
        assertThrows(IllegalStateException.class, () -> PatientRules.document(null, "1234567890101"));
        assertThrows(IllegalStateException.class, () -> PatientRules.document("DPI", null));
        assertThrows(IllegalStateException.class, () -> PatientRules.document("CEDULA", "123"));
    }

    @Test
    void lasListasCerradasNormalizanYExplicanElError() {
        assertEquals("whatsapp", PatientRules.oneOf("WhatsApp", PatientRules.CHANNELS, "Canal"));
        assertEquals("AB+", PatientRules.oneOf("ab+", PatientRules.BLOOD_TYPES, "Tipo de sangre"));
        assertNull(PatientRules.oneOf("  ", PatientRules.SEXES, "Sexo"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> PatientRules.oneOf("Z", PatientRules.SEXES, "Sexo"));
        assertTrue(e.getMessage().contains("F, M, I"));
    }

    @Test
    void edadYFechaDeNacimiento() {
        assertEquals(Integer.valueOf(17), PatientRules.age(LocalDate.of(2008, 10, 31), HOY));
        assertEquals(Integer.valueOf(18), PatientRules.age(LocalDate.of(2008, 10, 30), HOY));
        assertNull(PatientRules.age(null, HOY));
        assertThrows(IllegalStateException.class, () -> PatientRules.validateBirthDate(HOY.plusDays(1), HOY));
        assertThrows(IllegalStateException.class, () -> PatientRules.validateBirthDate(HOY.minusYears(131), HOY));
        assertDoesNotThrow(() -> PatientRules.validateBirthDate(HOY, HOY));
    }

    @Test
    void unMenorNecesitaTutorLegal() {
        assertThrows(IllegalStateException.class, () -> PatientRules.validateGuardians(List.of(), null, 10));
        assertThrows(IllegalStateException.class, () -> PatientRules.validateGuardians(List.of(pagador(5L)), null, 10));
        assertDoesNotThrow(() -> PatientRules.validateGuardians(List.of(tutor(5L)), null, 10));
        assertDoesNotThrow(() -> PatientRules.validateGuardians(null, null, 30));
        assertDoesNotThrow(() -> PatientRules.validateGuardians(null, null, null), "sin fecha de nacimiento no se exige");
    }

    @Test
    void responsablesSinRepetirConUnSoloPagadorYNuncaElMismoPaciente() {
        assertThrows(IllegalStateException.class,
                () -> PatientRules.validateGuardians(List.of(tutor(7L)), 7L, 30));
        assertThrows(IllegalStateException.class,
                () -> PatientRules.validateGuardians(List.of(tutor(5L), pagador(5L)), 7L, 30));
        assertThrows(IllegalStateException.class,
                () -> PatientRules.validateGuardians(List.of(pagador(5L), pagador(6L)), 7L, 30));
        assertThrows(IllegalStateException.class, () -> PatientRules.validateGuardians(
                List.of(new PatientDtos.GuardianData(5L, "Tío", false, false)), 7L, 30));
        assertDoesNotThrow(() -> PatientRules.validateGuardians(
                List.of(new PatientDtos.GuardianData(5L, "Madre", true, true), tutor(6L)), 7L, 8));
    }

    @Test
    void laFechaDeFallecimientoSoloAplicaAFallecido() {
        assertNull(PatientRules.deceasedOn("activo", HOY, HOY));
        assertEquals(HOY, PatientRules.deceasedOn("fallecido", HOY, HOY));
        assertNull(PatientRules.deceasedOn("fallecido", null, HOY));
        assertThrows(IllegalStateException.class, () -> PatientRules.deceasedOn("fallecido", HOY.plusDays(1), HOY));
    }

    @Test
    void unAntecedenteFamiliarExigeParentesco() {
        assertThrows(IllegalStateException.class, () -> PatientRules.validateHistory(List.of(
                new PatientDtos.HistoryData("familiar", "Diabetes", "E11", null, null, null))));
        assertDoesNotThrow(() -> PatientRules.validateHistory(List.of(
                new PatientDtos.HistoryData("familiar", "Diabetes", "E11", "Padre", null, null),
                new PatientDtos.HistoryData("cronica", "Asma", null, null, "2015", null))));
        assertThrows(IllegalStateException.class, () -> PatientRules.validateHistory(List.of(
                new PatientDtos.HistoryData("rara", "x", null, null, null, null))));
    }

    @Test
    void laBusquedaNoAceptaComodinesYReconoceTelefonos() {
        assertEquals("ana", PatientRules.searchText(" %ana_ "));
        assertEquals("", PatientRules.searchText(null));
        assertEquals("30063310", PatientRules.phoneDigits("+502 3006-3310"));
        assertEquals("30063310", PatientRules.phoneDigits("50230063310"));
        assertEquals("", PatientRules.phoneDigits("Ana López"));
        assertEquals("", PatientRules.phoneDigits("3006"));
    }
}
