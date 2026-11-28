package com.erp_maya.accounting.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El plan de cuentas es un árbol completo: nivel N cuelga de uno de nivel N − 1. */
class AccountTreeRulesTest {

    @Test
    void unaDeNivelUnoEsRaiz() {
        assertNull(AccountTreeRules.validar(1, null, null));
        assertTrue(AccountTreeRules.validar(1, "1", 1).contains("raíz"));
    }

    @Test
    void noExisteUnNivelDosSinSuNivelUno() {
        String error = AccountTreeRules.validar(2, null, null);
        assertNotNull(error);
        assertTrue(error.contains("nivel 1"), error);
        assertNotNull(AccountTreeRules.validar(4, null, null));
    }

    @Test
    void cuelgaDeLaDelNivelAnteriorNoDeOtra() {
        assertNull(AccountTreeRules.validar(2, "1", 1));
        assertNull(AccountTreeRules.validar(4, "1101", 3));
        // Saltarse un nivel (4 bajo 2) o colgar de una del mismo nivel no vale.
        assertTrue(AccountTreeRules.validar(4, "11", 2).contains("nivel 3"));
        assertNotNull(AccountTreeRules.validar(3, "1102", 3));
        assertNotNull(AccountTreeRules.validar(3, "X", null));
    }

    @Test
    void elNivelTieneQueSerValido() {
        assertNotNull(AccountTreeRules.validar(null, null, null));
        assertNotNull(AccountTreeRules.validar(0, null, null));
        assertNotNull(AccountTreeRules.validar(AccountTreeRules.NIVEL_MAXIMO + 1, "1", AccountTreeRules.NIVEL_MAXIMO));
    }

    /**
     * El catálogo mínimo que se siembra a una empresa nueva es un árbol
     * completo: cada cuenta de detalle y cada grupo tiene su padre del nivel
     * anterior entre los grupos sembrados.
     */
    @Test
    void elCatalogoMinimoEsUnArbolCompleto() {
        Map<String, Integer> nivelPorCodigo = new HashMap<>();
        DefaultPostingTemplates.GROUPS.forEach(g -> nivelPorCodigo.put(g.code(), g.level()));

        for (var g : DefaultPostingTemplates.GROUPS) {
            String padre = DefaultPostingTemplates.parentCode(g.code());
            Integer nivelPadre = padre != null ? nivelPorCodigo.get(padre) : null;
            assertNull(AccountTreeRules.validar(g.level(), padre, nivelPadre), "grupo " + g.code());
            if (padre != null) assertTrue(nivelPorCodigo.containsKey(padre), "falta el grupo " + padre);
        }
        for (var a : DefaultPostingTemplates.ACCOUNTS) {
            String padre = DefaultPostingTemplates.parentCode(a.code());
            assertNull(AccountTreeRules.validar(DefaultPostingTemplates.DETAIL_LEVEL, padre, nivelPorCodigo.get(padre)),
                    "cuenta " + a.code());
        }
        // Un grupo tiene el saldo normal de su clase (1 Activo débito, 2 Pasivo crédito…).
        Map<String, String> saldoPorCodigo = new HashMap<>();
        DefaultPostingTemplates.GROUPS.forEach(g -> saldoPorCodigo.put(g.code(), g.normalBalance()));
        for (var a : DefaultPostingTemplates.ACCOUNTS) {
            assertEquals(saldoPorCodigo.get(a.code().substring(0, 1)), a.normalBalance(), "saldo de " + a.code());
        }
    }

    /**
     * La migración 098 crea para las empresas que ya existen los mismos grupos
     * que Java siembra a las nuevas, y solo bajo las cuentas del catálogo mínimo.
     */
    @Test
    void laMigracionCreaLosMismosGruposQueJava() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/changelog/changes/098_account_tree.sql")) {
            assertNotNull(in, "no se encontró la migración 098");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Pattern fila = Pattern.compile("\\('(\\d+)',\\s*'([^']+)',\\s*'(debit|credit)',\\s*(\\d)\\)");
        List<String> enSql = new ArrayList<>();
        Matcher m = fila.matcher(sql);
        while (m.find()) enSql.add(String.join("|", m.group(1), m.group(2), m.group(3), m.group(4)));

        List<String> enJava = DefaultPostingTemplates.GROUPS.stream()
                .map(g -> String.join("|", g.code(), g.name(), g.normalBalance(), String.valueOf(g.level())))
                .toList();
        assertEquals(enJava, enSql);

        for (var a : DefaultPostingTemplates.ACCOUNTS) {
            assertTrue(sql.contains("'" + a.code() + "'"), "la 098 no considera la cuenta " + a.code());
        }
    }
}
