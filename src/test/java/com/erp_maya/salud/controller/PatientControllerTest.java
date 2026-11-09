package com.erp_maya.salud.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatientControllerTest {

    @Test
    void losIdsDelLookupIgnoranLoQueNoEsNumero() {
        assertEquals(List.of(1L, 2L, 3L), PatientController.ids("1, 2,x,3"));
        assertEquals(List.of(), PatientController.ids(null));
        assertEquals(List.of(), PatientController.ids("  "));
    }
}
