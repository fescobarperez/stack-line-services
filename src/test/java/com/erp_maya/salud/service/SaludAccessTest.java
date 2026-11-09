package com.erp_maya.salud.service;

import com.erp_maya.platform.service.ModuleCatalog;
import io.micronaut.security.authentication.Authentication;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Permisos de Salud: la ficha y el expediente clínico se validan en el servidor. */
class SaludAccessTest {

    @Test
    void recepcionVeLaFichaPeroNoLoClinico() {
        SaludAccess.Actor recepcion = new SaludAccess.Actor(3L, Set.of("patients|ver", "patients|crear"));
        assertTrue(recepcion.can(SaludAccess.PATIENTS, SaludAccess.VER));
        assertFalse(recepcion.can(SaludAccess.CLINICAL, SaludAccess.VER));
        SaludForbiddenException e = assertThrows(SaludForbiddenException.class,
                () -> recepcion.require(SaludAccess.CLINICAL, SaludAccess.VER));
        assertTrue(e.getMessage().contains("expediente clínico"));
    }

    @Test
    void elAdministradorConAsteriscoPuedeTodo() {
        SaludAccess.Actor admin = new SaludAccess.Actor(1L, Set.of("*"));
        assertDoesNotThrow(() -> admin.require(SaludAccess.CLINICAL, SaludAccess.EDITAR));
        assertDoesNotThrow(() -> admin.require(SaludAccess.PATIENTS, SaludAccess.CREAR));
    }

    @Test
    void verNoDaPermisoDeEditarNiUnaClaveParecida() {
        SaludAccess.Actor medico = new SaludAccess.Actor(2L, Set.of("clinical_record|ver"));
        assertFalse(medico.can(SaludAccess.CLINICAL, SaludAccess.EDITAR));
        assertFalse(SaludAccess.permite(Set.of("patients|verx"), SaludAccess.PATIENTS, SaludAccess.VER));
        assertFalse(SaludAccess.permite(null, SaludAccess.PATIENTS, SaludAccess.VER));
    }

    @Test
    void elActorSeArmaDesdeElToken() {
        Authentication auth = Authentication.build("9", List.of("patients|ver"),
                Map.of("userId", 9, "permissions", List.of("clinical_record|ver")));
        SaludAccess.Actor a = SaludAccess.of(auth);
        assertEquals(Long.valueOf(9), a.userId());
        assertTrue(a.can(SaludAccess.PATIENTS, SaludAccess.VER));
        assertTrue(a.can(SaludAccess.CLINICAL, SaludAccess.VER));

        assertFalse(a.service());

        SaludAccess.Actor anonimo = SaludAccess.of(null);
        assertNull(anonimo.userId());
        assertFalse(anonimo.can(SaludAccess.PATIENTS, SaludAccess.VER));
    }

    @Test
    void lasRutasDeSaludPertenecenAlModuloSalud() {
        assertEquals("salud", ModuleCatalog.moduleFor("/api/salud/patients"));
        assertEquals("salud", ModuleCatalog.moduleFor("/api/salud/patients/12/clinical"));
        assertTrue(ModuleCatalog.CODES.contains("salud"));
        assertNull(ModuleCatalog.moduleFor("/api/saludx"));
    }

    @Test
    void elTokenDeServicioMarcaAlAsistente() {
        Authentication auth = Authentication.build("3", List.of(),
                Map.of("userId", 3, "kind", "service", "permissions", List.of("agenda|crear")));
        SaludAccess.Actor a = SaludAccess.of(auth);
        assertTrue(a.service());
        assertTrue(a.can(SaludAccess.AGENDA, SaludAccess.CREAR));
        assertFalse(a.can(SaludAccess.CLINICAL, SaludAccess.VER));
    }
}
