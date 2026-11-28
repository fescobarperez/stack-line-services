package com.erp_maya.accounting.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La plantilla decide cuentas y montos de cada partida: aquí no puede salir nada mal. */
class TemplateEvaluatorTest {

    private static final String D = TemplateEvaluator.DEBIT;
    private static final String C = TemplateEvaluator.CREDIT;

    private static TemplateEvaluator.LineSpec linea(String side, Long cuenta, boolean doc, String... montos) {
        return new TemplateEvaluator.LineSpec(side, cuenta, doc, List.of(montos), null, true);
    }

    private static Map<String, BigDecimal> montos(String... pares) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put(pares[i], new BigDecimal(pares[i + 1]));
        return m;
    }

    private static TemplateEvaluator.Context ctx(Map<String, BigDecimal> montos, Long doc, Long cc) {
        return new TemplateEvaluator.Context(montos, doc, cc, "Venta F-1", "F-1", "Ana");
    }

    /** Las plantillas por defecto con una cuenta inventada por código. */
    private static List<TemplateEvaluator.LineSpec> specs(DefaultPostingTemplates.DefaultTemplate t) {
        return t.lines().stream().map(l -> new TemplateEvaluator.LineSpec(l.side(),
                Long.valueOf(l.accountCode()), l.usesDocumentAccount(), l.amounts(), l.description(),
                l.useCostCenter())).toList();
    }

    @Test
    void ventaDeContadoMueveCajaIngresosEIva() {
        var tpl = DefaultPostingTemplates.TEMPLATES.get(0);
        assertEquals(PostingCase.POS_VENTA_CONTADO, tpl.useCase());

        var lineas = TemplateEvaluator.evaluate(specs(tpl), ctx(montos("total", "224", "base", "200", "iva", "24"), null, 7L));

        assertEquals(3, lineas.size());
        assertEquals(110101L, lineas.get(0).accountId());
        assertEquals(0, new BigDecimal("224").compareTo(lineas.get(0).debit()));
        assertEquals(410101L, lineas.get(1).accountId());
        assertEquals(0, new BigDecimal("200").compareTo(lineas.get(1).credit()));
        assertEquals(0, new BigDecimal("24").compareTo(lineas.get(2).credit()));
        assertEquals("Venta F-1", lineas.get(0).description());
        assertEquals("Ingresos F-1", lineas.get(1).description());
        assertEquals(7L, lineas.get(2).costCenterId());
        assertEquals(0, TemplateEvaluator.difference(lineas).signum());
    }

    @Test
    void laNotaDeCreditoConMontosNegativosTambienCuadra() {
        var lineas = TemplateEvaluator.evaluate(specs(DefaultPostingTemplates.TEMPLATES.get(0)),
                ctx(montos("total", "-112", "base", "-100", "iva", "-12"), null, null));
        assertEquals(0, TemplateEvaluator.difference(lineas).signum());
        assertTrue(lineas.get(0).debit().signum() < 0);
    }

    @Test
    void laCuentaDelDocumentoGanaYLaFijaEsElRespaldo() {
        List<TemplateEvaluator.LineSpec> cobro = List.of(linea(D, 110101L, true, "monto"), linea(C, 110201L, false, "monto"));

        var conBanco = TemplateEvaluator.evaluate(cobro, ctx(montos("monto", "50"), 990L, null));
        assertEquals(990L, conBanco.get(0).accountId());

        var sinDocumento = TemplateEvaluator.evaluate(cobro, ctx(montos("monto", "50"), null, null));
        assertEquals(110101L, sinDocumento.get(0).accountId());

        List<TemplateEvaluator.LineSpec> sinRespaldo = List.of(linea(D, null, true, "monto"), linea(C, 110201L, false, "monto"));
        assertThrows(IllegalStateException.class,
                () -> TemplateEvaluator.evaluate(sinRespaldo, ctx(montos("monto", "50"), null, null)));
    }

    @Test
    void unaLineaPuedeSumarVariosMontos() {
        var l = linea(C, 1L, false, "base", "iva");
        assertEquals(0, new BigDecimal("112").compareTo(TemplateEvaluator.amount(l, montos("base", "100", "iva", "12"))));
    }

    @Test
    void elCentroDeCostoSoloVaSiLaLineaLoPide() {
        var sinCentro = new TemplateEvaluator.LineSpec(D, 1L, false, List.of("costo"), "x", false);
        var conCentro = new TemplateEvaluator.LineSpec(C, 2L, false, List.of("costo"), "x", true);
        var lineas = TemplateEvaluator.evaluate(List.of(sinCentro, conCentro), ctx(montos("costo", "10"), null, 5L));
        assertNull(lineas.get(0).costCenterId());
        assertEquals(5L, lineas.get(1).costCenterId());
    }

    @Test
    void validarDiceQueEstaMal() {
        var caso = PostingCase.POS_VENTA_CONTADO;

        assertTrue(TemplateEvaluator.validate(caso, List.of()).get(0).contains("al menos una línea"));

        var errores = TemplateEvaluator.validate(caso, List.of(
                linea(D, null, false, "total"),            // sin cuenta
                linea(C, 2L, false, "propina"),            // monto que la venta no tiene
                linea(C, 3L, true, "iva")));               // la venta no trae cuenta del documento
        assertTrue(errores.stream().anyMatch(e -> e.contains("elige la cuenta")));
        assertTrue(errores.stream().anyMatch(e -> e.contains("«propina»")));
        assertTrue(errores.stream().anyMatch(e -> e.contains("no trae cuenta del documento")));

        var soloDebe = TemplateEvaluator.validate(caso, List.of(linea(D, 1L, false, "total")));
        assertTrue(soloDebe.stream().anyMatch(e -> e.contains("al menos una línea al débito y una al crédito")));
    }

    @Test
    void validarDetectaQueNoCuadraConElEjemplo() {
        // Débito el total contra crédito solo la base: falta el IVA.
        var errores = TemplateEvaluator.validate(PostingCase.POS_VENTA_CONTADO,
                List.of(linea(D, 1L, false, "total"), linea(C, 2L, false, "base")));
        assertEquals(1, errores.size());
        assertTrue(errores.get(0).contains("no cuadra"));
    }

    @Test
    void textoConVariables() {
        var c = ctx(Map.of(), null, null);
        assertEquals("Cobro F-1 · Ana", TemplateEvaluator.text("Cobro {ref} · {tercero}", c));
        assertEquals("Venta F-1", TemplateEvaluator.text("  ", c));
        assertEquals("Venta F-1", TemplateEvaluator.text(null, c));
    }

    @Test
    void losEjemplosCumplenSusInvariantes() {
        for (PostingCase c : PostingCase.values()) {
            for (String k : c.example().keySet()) assertTrue(c.hasAmount(k), c + " ejemplo con monto ajeno " + k);
            if (c.hasAmount("total")) {
                var e = c.example();
                assertEquals(0, e.get("total").compareTo(e.get("base").add(e.get("iva"))), c.code());
            }
        }
    }

    @Test
    void cadaCasoTienePlantillaPorDefectoYTodasValidan() {
        Set<PostingCase> cubiertos = new HashSet<>();
        Set<String> codigos = new HashSet<>();
        DefaultPostingTemplates.ACCOUNTS.forEach(a -> codigos.add(a.code()));
        for (var t : DefaultPostingTemplates.TEMPLATES) {
            assertTrue(cubiertos.add(t.useCase()), "dos plantillas por defecto para " + t.useCase());
            assertEquals(List.of(), TemplateEvaluator.validate(t.useCase(), specs(t)), t.name());
            t.lines().forEach(l -> assertTrue(codigos.contains(l.accountCode()), "cuenta fuera del catálogo: " + l.accountCode()));
        }
        assertEquals(Set.of(PostingCase.values()), cubiertos);
    }

    /**
     * Las migraciones 095 y 097 siembran en SQL las mismas plantillas que
     * DefaultPostingTemplates siembra en Java para las empresas nuevas. Si una
     * cambia sin la otra, empresas viejas y nuevas contabilizarían distinto.
     */
    @Test
    void laMigracionSiembraLoMismoQueJava() throws IOException {
        // Las plantillas por defecto se siembran en 095 (venta, cobro, proyecto) y 097 (anulación).
        StringBuilder todo = new StringBuilder();
        for (String archivo : List.of("095_journal_templates.sql", "097_void_posting_templates.sql")) {
            try (InputStream in = getClass().getResourceAsStream("/db/changelog/changes/" + archivo)) {
                assertTrue(in != null, "no se encontró la migración " + archivo);
                todo.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        String sql = todo.toString();
        Map<String, String> codigoPorClave = new HashMap<>();
        DefaultPostingTemplates.ACCOUNTS.forEach(a -> codigoPorClave.put(a.postingKey(), a.code()));

        Pattern fila = Pattern.compile("\\('([a-z.]+)',\\s*(\\d+),\\s*'(debit|credit)',\\s*'(posting\\.[a-z_]+)',\\s*"
                + "(TRUE|FALSE),\\s*'([a-z_,]+)',\\s*'([^']*)',\\s*(TRUE|FALSE)\\)");
        List<String> enSql = new ArrayList<>();
        Matcher m = fila.matcher(sql);
        while (m.find()) {
            enSql.add(String.join("|", m.group(1), m.group(2), m.group(3), codigoPorClave.get(m.group(4)),
                    m.group(5).toLowerCase(), m.group(6), m.group(7), m.group(8).toLowerCase()));
        }

        List<String> enJava = new ArrayList<>();
        for (var t : DefaultPostingTemplates.TEMPLATES) {
            int pos = 0;
            for (var l : t.lines()) {
                enJava.add(String.join("|", t.useCase().code(), String.valueOf(++pos), l.side(), l.accountCode(),
                        String.valueOf(l.usesDocumentAccount()), String.join(",", l.amounts()), l.description(),
                        String.valueOf(l.useCostCenter())));
            }
        }
        // Ordenadas: Java las lista por caso de uso y el SQL por migración.
        assertEquals(enJava.stream().sorted().toList(), enSql.stream().sorted().toList());
    }
}
