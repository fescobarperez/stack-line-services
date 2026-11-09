package com.erp_maya.salud.service;

import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.PatientPackage;
import com.erp_maya.salud.dto.CajaDtos;
import io.micronaut.core.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reglas de caja y seguimiento de Salud (fase 7), sin base ni HTTP para
 * probarlas solas: cómo se reparte un cobro entre anticipo y caja, qué
 * paquete sirve para una cita, el avance de un plan y los números de los
 * reportes.
 */
public final class CajaRules {

    private CajaRules() {}

    // Métodos del cobro (salud_charges.method)
    public static final String VENTA = "venta";
    public static final String PAQUETE = "paquete";
    public static final String ANTICIPO = "anticipo";
    public static final String MIXTO = "mixto";
    public static final String CORTESIA = "cortesia";

    public static final String VIGENTE = "vigente";
    public static final String ANULADO = "anulado";

    // Paquete del paciente
    public static final String PK_ACTIVO = "activo";
    public static final String PK_AGOTADO = "agotado";
    public static final String PK_ANULADO = "anulado";

    // Movimientos del anticipo
    public static final String CR_ANTICIPO = "anticipo";
    public static final String CR_APLICADO = "aplicado";
    public static final String CR_REVERSO = "reverso";

    // Plan de tratamiento
    public static final String PLAN_ACTIVO = "activo";
    public static final String PLAN_COMPLETADO = "completado";
    public static final String PLAN_CANCELADO = "cancelado";

    public static final Set<String> FEELINGS = Set.of("mejor", "igual", "peor");

    /** Formas de pago que entiende el arqueo (efectivo y tarjeta se cuadran aparte). */
    public static final Set<String> PAGOS = Set.of("efectivo", "tarjeta", "transferencia", "cheque");

    /** Citas en las que no se cobra: no se atendieron. */
    public static final Set<String> NO_COBRABLES = Set.of("cancelada", "no_asistio");

    /** Citas que siguen en pie (cuentan como sesión agendada de un plan). */
    public static final Set<String> VIGENTES = Set.of("solicitada", "confirmada", "en_sala", "en_atencion");

    public static final BigDecimal MAX_MONTO = new BigDecimal("999999.99");

    static BigDecimal cero(@Nullable BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static BigDecimal q(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Un monto válido para cobrar: positivo (o cero si se permite), con dos decimales. */
    public static BigDecimal monto(@Nullable BigDecimal v, String que, boolean ceroOk) {
        if (v == null) throw new IllegalStateException("Indica " + que + ".");
        if (v.signum() < 0 || (!ceroOk && v.signum() == 0)) {
            throw new IllegalStateException(que.substring(0, 1).toUpperCase(Locale.ROOT) + que.substring(1)
                    + (ceroOk ? " no puede ser negativo." : " debe ser mayor que cero."));
        }
        if (v.compareTo(MAX_MONTO) > 0) throw new IllegalStateException("El monto es demasiado alto.");
        if (v.scale() > 2 && v.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("Usa como máximo dos decimales en " + que + ".");
        }
        return q(v);
    }

    /** «Efectivo», «tarjeta»…; sin valor, efectivo. */
    public static String pago(@Nullable String v) {
        if (v == null || v.isBlank()) return "efectivo";
        String p = v.trim().toLowerCase(Locale.ROOT);
        if (!PAGOS.contains(p)) throw new IllegalStateException("Forma de pago no reconocida: " + v);
        return p;
    }

    /** El reparto de un cobro: cuánto sale del anticipo y cuánto se factura en caja. */
    public record Reparto(BigDecimal total, BigDecimal credito, BigDecimal caja, String metodo) {}

    /**
     * Total = precio − descuento; si se usa el anticipo, sale de ahí lo que
     * alcance y el resto se factura. El método dice de dónde salió el dinero.
     */
    public static Reparto repartir(BigDecimal precio, @Nullable BigDecimal descuento, BigDecimal saldo, boolean usarAnticipo) {
        BigDecimal p = monto(precio, "el precio", true);
        BigDecimal d = monto(cero(descuento), "el descuento", true);
        if (d.compareTo(p) > 0) throw new IllegalStateException("El descuento no puede ser mayor que el precio.");
        BigDecimal total = p.subtract(d);
        BigDecimal credito = usarAnticipo ? total.min(cero(saldo).max(BigDecimal.ZERO)) : BigDecimal.ZERO;
        BigDecimal caja = total.subtract(credito);
        String metodo = credito.signum() > 0 ? (caja.signum() > 0 ? MIXTO : ANTICIPO) : VENTA;
        return new Reparto(total, q(credito), q(caja), metodo);
    }

    /** Lo que vale una sesión de un paquete: precio ÷ sesiones. */
    public static BigDecimal valorSesion(BigDecimal precio, int sesiones) {
        if (sesiones <= 0) return q(cero(precio));
        return cero(precio).divide(BigDecimal.valueOf(sesiones), 2, RoundingMode.HALF_UP);
    }

    /** El paquete sirve hoy para una cita de ese tipo: activo, del tipo, con sesiones y sin vencer. */
    public static boolean sirve(PatientPackage p, @Nullable Long typeId, LocalDate hoy) {
        return PK_ACTIVO.equals(p.getStatus())
                && p.getSessionsUsed() < p.getSessionsTotal()
                && (p.getExpiresOn() == null || !p.getExpiresOn().isBefore(hoy))
                && (typeId == null || typeId.equals(p.getTypeId()));
    }

    /** Los que sirven, en el orden en que conviene usarlos: el que vence primero, luego el más viejo. */
    public static List<PatientPackage> aplicables(List<PatientPackage> paquetes, Long typeId, LocalDate hoy) {
        return paquetes.stream().filter(p -> sirve(p, typeId, hoy))
                .sorted(Comparator.comparing((PatientPackage p) -> p.getExpiresOn() == null ? LocalDate.MAX : p.getExpiresOn())
                        .thenComparing(PatientPackage::getPurchasedAt))
                .toList();
    }

    /** Descuenta una sesión; al usar la última queda agotado. */
    public static void consumir(PatientPackage p) {
        if (p.getSessionsUsed() >= p.getSessionsTotal()) throw new IllegalStateException("El paquete ya no tiene sesiones.");
        p.setSessionsUsed(p.getSessionsUsed() + 1);
        if (p.getSessionsUsed() >= p.getSessionsTotal()) p.setStatus(PK_AGOTADO);
    }

    /** Devuelve la sesión de un cobro anulado; si estaba agotado vuelve a activo. */
    public static void devolver(PatientPackage p) {
        if (p.getSessionsUsed() > 0) p.setSessionsUsed(p.getSessionsUsed() - 1);
        if (PK_AGOTADO.equals(p.getStatus()) && p.getSessionsUsed() < p.getSessionsTotal()) p.setStatus(PK_ACTIVO);
    }

    public static CajaDtos.PatientPackageView toView(PatientPackage p, @Nullable String typeName, LocalDate hoy) {
        return new CajaDtos.PatientPackageView(p.getId(), p.getPackageId(), p.getName(), p.getTypeId(), typeName,
                p.getSessionsTotal(), p.getSessionsUsed(), Math.max(0, p.getSessionsTotal() - p.getSessionsUsed()),
                p.getPrice(), p.getSaleId(), p.getPurchasedAt(), p.getExpiresOn(), p.getStatus(), sirve(p, null, hoy));
    }

    // ── Validaciones de catálogo y plan ──────────────────────────────────

    public static int sesionesPaquete(@Nullable Integer v) {
        if (v == null || v < 1 || v > 200) throw new IllegalStateException("Un paquete lleva de 1 a 200 sesiones.");
        return v;
    }

    public static @Nullable Integer vigencia(@Nullable Integer v) {
        if (v == null || v == 0) return null;
        if (v < 1 || v > 1095) throw new IllegalStateException("La vigencia va de 1 a 1095 días (o vacía si no vence).");
        return v;
    }

    public static int sesionesPlan(@Nullable Integer v) {
        if (v == null || v < 1 || v > 100) throw new IllegalStateException("Un plan lleva de 1 a 100 sesiones.");
        return v;
    }

    public static @Nullable Integer intervalo(@Nullable Integer v) {
        if (v == null || v == 0) return null;
        if (v < 1 || v > 90) throw new IllegalStateException("El intervalo entre sesiones va de 1 a 90 días.");
        return v;
    }

    public static int controlReminderDays(@Nullable Integer v) {
        if (v == null || v < 1 || v > 14) throw new IllegalStateException("El recordatorio del control va de 1 a 14 días antes.");
        return v;
    }

    public static String feeling(@Nullable String v) {
        String f = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        if (!FEELINGS.contains(f)) throw new IllegalStateException("La respuesta debe ser mejor, igual o peor.");
        return f;
    }

    // ── Plan de tratamiento ──────────────────────────────────────────────

    /** Avance del plan con sus citas. */
    public record Avance(int hechas, int agendadas, int faltan, @Nullable LocalDate siguiente) {}

    /**
     * Atendidas, agendadas (por venir) y las que faltan por agendar. La
     * siguiente fecha sugerida es la de la última cita en pie más el
     * intervalo (nunca antes de hoy); sin citas, hoy.
     */
    public static Avance avance(List<Appointment> citas, int sesiones, @Nullable Integer intervalo, LocalDate hoy) {
        int hechas = 0;
        int agendadas = 0;
        LocalDate ultima = null;
        for (Appointment a : citas) {
            boolean hecha = "completada".equals(a.getStatus());
            boolean enPie = VIGENTES.contains(a.getStatus());
            if (hecha) hechas++;
            if (enPie) agendadas++;
            if (hecha || enPie) {
                LocalDate d = a.getStartsAt().atZone(AgendaRules.GUATEMALA).toLocalDate();
                if (ultima == null || d.isAfter(ultima)) ultima = d;
            }
        }
        int faltan = Math.max(0, sesiones - hechas - agendadas);
        LocalDate siguiente = null;
        if (faltan > 0) {
            siguiente = ultima == null ? hoy : ultima.plusDays(intervalo == null ? 7 : intervalo);
            if (siguiente.isBefore(hoy)) siguiente = hoy;
        }
        return new Avance(hechas, agendadas, faltan, siguiente);
    }

    // ── Reportes ─────────────────────────────────────────────────────────

    /** Una cita del periodo, con lo que importa para el reporte. */
    public record CitaHecho(LocalDate fecha, Long professionalId, String profesional, String estado) {}

    /** Un cobro vigente del periodo. */
    public record CobroHecho(Long typeId, String tipo, String metodo, BigDecimal valor, BigDecimal caja, BigDecimal credito) {}

    /** No asistió ÷ (atendidas + no asistió), en % con un decimal; null si no hay base. */
    public static @Nullable Double tasaInasistencia(int atendidas, int noAsistio) {
        int base = atendidas + noAsistio;
        if (base == 0) return null;
        return BigDecimal.valueOf(noAsistio * 100.0 / base).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static List<CajaDtos.DayRow> porDia(List<CitaHecho> citas) {
        Map<LocalDate, int[]> m = new TreeMap<>();
        for (CitaHecho c : citas) cuenta(m.computeIfAbsent(c.fecha(), k -> new int[4]), c.estado());
        List<CajaDtos.DayRow> out = new ArrayList<>();
        m.forEach((d, n) -> out.add(new CajaDtos.DayRow(d, n[0], n[1], n[2], n[3])));
        return out;
    }

    /** Por profesional, el de más citas primero. */
    public static List<CajaDtos.ProfessionalRow> porProfesional(List<CitaHecho> citas) {
        Map<Long, int[]> m = new LinkedHashMap<>();
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (CitaHecho c : citas) {
            nombres.putIfAbsent(c.professionalId(), c.profesional());
            cuenta(m.computeIfAbsent(c.professionalId(), k -> new int[4]), c.estado());
        }
        return m.entrySet().stream()
                .map(e -> new CajaDtos.ProfessionalRow(e.getKey(), nombres.get(e.getKey()), e.getValue()[0],
                        e.getValue()[1], e.getValue()[2], e.getValue()[3], tasaInasistencia(e.getValue()[1], e.getValue()[2])))
                .sorted(Comparator.comparingInt(CajaDtos.ProfessionalRow::appointments).reversed()
                        .thenComparing(CajaDtos.ProfessionalRow::name))
                .toList();
    }

    /** Ingresos por servicio (tipo de cita), el de más valor primero. */
    public static List<CajaDtos.ServiceRow> porServicio(List<CobroHecho> cobros) {
        Map<Long, String> nombres = new LinkedHashMap<>();
        Map<Long, int[]> n = new LinkedHashMap<>();
        Map<Long, BigDecimal[]> s = new LinkedHashMap<>();
        for (CobroHecho c : cobros) {
            nombres.putIfAbsent(c.typeId(), c.tipo());
            int[] k = n.computeIfAbsent(c.typeId(), x -> new int[3]);
            k[0]++;
            if (PAQUETE.equals(c.metodo())) k[1]++;
            if (CORTESIA.equals(c.metodo())) k[2]++;
            BigDecimal[] v = s.computeIfAbsent(c.typeId(), x -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(cero(c.valor()));
            v[1] = v[1].add(cero(c.caja()));
            v[2] = v[2].add(cero(c.credito()));
        }
        return n.keySet().stream()
                .map(id -> new CajaDtos.ServiceRow(id, nombres.get(id), n.get(id)[0], n.get(id)[1], n.get(id)[2],
                        q(s.get(id)[0]), q(s.get(id)[1]), q(s.get(id)[2])))
                .sorted(Comparator.comparing(CajaDtos.ServiceRow::serviceValue).reversed()
                        .thenComparing(CajaDtos.ServiceRow::name))
                .toList();
    }

    public static CajaDtos.Totals totales(List<CitaHecho> citas, List<CobroHecho> cobros,
                                          BigDecimal paquetes, BigDecimal anticipos) {
        int[] n = new int[4];
        citas.forEach(c -> cuenta(n, c.estado()));
        BigDecimal valor = BigDecimal.ZERO;
        BigDecimal caja = BigDecimal.ZERO;
        BigDecimal credito = BigDecimal.ZERO;
        for (CobroHecho c : cobros) {
            valor = valor.add(cero(c.valor()));
            caja = caja.add(cero(c.caja()));
            credito = credito.add(cero(c.credito()));
        }
        return new CajaDtos.Totals(n[0], n[1], n[2], n[3], tasaInasistencia(n[1], n[2]), cobros.size(), q(valor),
                q(caja), q(credito), q(cero(paquetes)), q(cero(anticipos)), q(caja.add(cero(paquetes)).add(cero(anticipos))));
    }

    /** [citas, atendidas, no asistió, canceladas] */
    private static void cuenta(int[] n, String estado) {
        n[0]++;
        switch (estado) {
            case "completada" -> n[1]++;
            case "no_asistio" -> n[2]++;
            case "cancelada" -> n[3]++;
            default -> { }
        }
    }

    /** El periodo del reporte: hasta un año, desde no después de hasta. */
    public static void checkPeriodo(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) throw new IllegalStateException("La fecha final es anterior a la inicial.");
        if (desde.plusDays(366).isBefore(hasta)) throw new IllegalStateException("El reporte abarca como máximo un año.");
    }
}
