package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.domain.AccountingPeriod;
import com.erp_maya.accounting.domain.JournalEntry;
import com.erp_maya.accounting.domain.JournalEntryLine;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.accounting.repository.AccountingPeriodRepository;
import com.erp_maya.accounting.repository.JournalEntryRepository;
import com.erp_maya.common.TenantContext;
import com.erp_maya.settings.domain.CompanySetting;
import com.erp_maya.settings.repository.CompanySettingRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * El único lugar por donde nace una partida contable.
 *
 * Antes cada módulo que quisiera contabilizar tenía que armar la partida a
 * mano —resolver cuentas, cuadrar débitos con créditos, encontrar el período—
 * y solo Activos Fijos lo hacía. Con cinco módulos haciéndolo cada uno a su
 * manera, la primera partida descuadrada era cuestión de tiempo.
 *
 * Aquí se centraliza lo que no puede salir mal: que sume igual de los dos
 * lados, que caiga en un período abierto, y que un mismo documento no se
 * contabilice dos veces.
 */
@Singleton
public class PostingService {

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;
    private final AccountingPeriodRepository periods;
    private final CompanySettingRepository settings;
    private final TenantContext tenant;

    public PostingService(JournalEntryRepository entries, AccountRepository accounts,
                          AccountingPeriodRepository periods, CompanySettingRepository settings,
                          TenantContext tenant) {
        this.entries = entries;
        this.accounts = accounts;
        this.periods = periods;
        this.settings = settings;
        this.tenant = tenant;
    }

    /** Un renglón por escribir. `accountKey` es la clave del mapeo, no un código. */
    /**
     * Un renglón por escribir. Normalmente la cuenta se resuelve por
     * `accountKey` (la clave del mapeo). Si `accountId` viene informado, esa
     * cuenta gana: es la que el usuario eligió en el propio documento —p.ej. la
     * caja concreta de un cobro en efectivo— y no la cuenta por defecto del rol.
     */
    public record Line(String accountKey, Long accountId, BigDecimal debit, BigDecimal credit,
                       String description, Long costCenterId) {

        public static Line debit(String key, BigDecimal amount, String desc, Long cc) {
            return new Line(key, null, amount, BigDecimal.ZERO, desc, cc);
        }

        public static Line credit(String key, BigDecimal amount, String desc, Long cc) {
            return new Line(key, null, BigDecimal.ZERO, amount, desc, cc);
        }

        /** Débito contra una cuenta explícita; `key` queda como respaldo si el id no resuelve. */
        public static Line debitAccount(String key, Long accountId, BigDecimal amount, String desc, Long cc) {
            return new Line(key, accountId, amount, BigDecimal.ZERO, desc, cc);
        }
    }

    /**
     * Registra una partida. Devuelve null si no había nada que registrar —una
     * venta de cero, por ejemplo— en vez de dejar una partida vacía en el libro.
     *
     * @param sourceType qué originó la partida: "sale", "payment", "purchase"…
     * @param sourceId   id del documento. Junto con sourceType impide el doble asiento.
     */
    @Transactional
    public JournalEntry post(String sourceType, Long sourceId, LocalDate date,
                             String description, String reference, List<Line> lines) {
        Long companyId = tenant.getCompanyId();

        // Las líneas en cero no se escriben: una venta exenta no debe dejar un
        // renglón de IVA en cero ensuciando la partida.
        List<Line> real = lines.stream()
                .filter(l -> l != null && (sig(l.debit()) || sig(l.credit())))
                .toList();
        if (real.isEmpty()) return null;

        JournalEntry entry = new JournalEntry();
        entry.setCompanyId(companyId);
        entry.setEntryDate(date != null ? date : LocalDate.now());
        entry.setEntryType("auto");
        entry.setDescription(description);
        entry.setReference(reference);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setPeriod(periodFor(entry.getEntryDate(), companyId));

        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (Line l : real) {
            Account acc = l.accountId() != null
                    ? accountById(l.accountId(), companyId)
                    : account(l.accountKey(), companyId);
            JournalEntryLine jl = new JournalEntryLine();
            jl.setCompanyId(companyId);
            jl.setAccount(acc);
            jl.setDebit(round(l.debit()));
            jl.setCredit(round(l.credit()));
            jl.setDescription(l.description());
            jl.setCostCenterId(l.costCenterId());
            entry.addLine(jl);
            debit = debit.add(jl.getDebit());
            credit = credit.add(jl.getCredit());
        }

        // La partida descuadrada es el error que corrompe la contabilidad en
        // silencio: se detecta aquí y se cae, en vez de guardarla.
        if (debit.compareTo(credit) != 0) {
            throw new IllegalStateException(String.format(
                    "Partida descuadrada en %s: débitos %s contra créditos %s. Diferencia %s.",
                    sourceType, debit, credit, debit.subtract(credit)));
        }

        entry.setTotalDebit(debit);
        entry.setTotalCredit(credit);
        entry.setStatus("posted");
        return entries.save(entry);
    }

    /**
     * Reversa una partida con su espejo. No se borra ni se edita: una partida
     * emitida es un hecho, y corregirla borrándola deja el libro sin rastro de
     * que existió.
     */
    @Transactional
    public JournalEntry reverse(Long entryId, String reason) {
        Long companyId = tenant.getCompanyId();
        JournalEntry orig = entries.findById(entryId)
                .filter(e -> e.getCompanyId().equals(companyId))
                .orElseThrow(() -> new IllegalStateException("Partida " + entryId + " no encontrada"));
        if ("void".equals(orig.getStatus())) {
            throw new IllegalStateException("La partida " + entryId + " ya fue reversada");
        }

        JournalEntry rev = new JournalEntry();
        rev.setCompanyId(companyId);
        rev.setEntryDate(LocalDate.now());
        rev.setEntryType("auto");
        rev.setDescription("Reversión de " + orig.getDescription()
                + (reason != null && !reason.isBlank() ? " · " + reason : ""));
        rev.setReference(orig.getReference());
        rev.setSourceType(orig.getSourceType());
        rev.setReversesEntryId(orig.getId());
        rev.setPeriod(periodFor(rev.getEntryDate(), companyId));

        // sourceId queda nulo a propósito: el índice único que impide el doble
        // asiento no debe contar la reversión como un segundo registro.
        for (JournalEntryLine l : orig.getLines()) {
            JournalEntryLine jl = new JournalEntryLine();
            jl.setCompanyId(companyId);
            jl.setAccount(l.getAccount());
            jl.setDebit(l.getCredit());   // espejo
            jl.setCredit(l.getDebit());
            jl.setDescription(l.getDescription());
            jl.setCostCenterId(l.getCostCenterId());
            rev.addLine(jl);
        }
        rev.setTotalDebit(orig.getTotalCredit());
        rev.setTotalCredit(orig.getTotalDebit());
        rev.setStatus("posted");

        orig.setStatus("void");
        entries.update(orig);
        return entries.save(rev);
    }

    // ── Interno ──────────────────────────────────────────────────────────

    /**
     * La cuenta configurada para una clave del mapeo.
     *
     * El mensaje nombra la clave que falta porque el error llega en medio de
     * una venta: quien lo lea necesita saber qué configurar, no que "faltó una
     * cuenta".
     */
    private Account account(String key, Long companyId) {
        String id = settings.findByCompanyIdAndSettingKey(companyId, key)
                .map(CompanySetting::getSettingValue)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay cuenta contable configurada para '" + key
                        + "'. Revísala en Contabilidad → Configuración."));
        return accounts.findById(Long.valueOf(id))
                .filter(a -> a.getCompanyId().equals(companyId))
                .orElseThrow(() -> new IllegalStateException(
                        "La cuenta configurada en '" + key + "' (id " + id + ") ya no existe."));
    }

    /**
     * La cuenta que el usuario eligió explícitamente en el documento. Se exige
     * que sea de detalle (allows_entries): postear contra una cuenta de
     * agrupación deja el mayor sin el desglose que la cuenta padre resume.
     */
    private Account accountById(Long accountId, Long companyId) {
        Account acc = accounts.findById(accountId)
                .filter(a -> a.getCompanyId().equals(companyId))
                .orElseThrow(() -> new IllegalStateException(
                        "La cuenta seleccionada (id " + accountId + ") no existe."));
        if (Boolean.FALSE.equals(acc.getAllowsEntries())) {
            throw new IllegalStateException(
                    "La cuenta " + acc.getCode() + " no admite partidas directas; elige una cuenta de detalle.");
        }
        return acc;
    }

    /**
     * El período que contiene la fecha. Si no existe, se crea el mes.
     *
     * Crear en vez de fallar es deliberado: bloquear una venta porque nadie
     * abrió el mes en Contabilidad convierte una tarea administrativa en una
     * caja registradora detenida. Un período cerrado sí bloquea — eso es una
     * decisión explícita de alguien.
     */
    private AccountingPeriod periodFor(LocalDate date, Long companyId) {
        AccountingPeriod p = periods.findByCompanyIdOrderByStartDateDesc(companyId).stream()
                .filter(x -> !date.isBefore(x.getStartDate()) && !date.isAfter(x.getEndDate()))
                .findFirst().orElse(null);

        if (p != null) {
            if ("closed".equalsIgnoreCase(p.getStatus())) {
                throw new IllegalStateException("El período " + p.getName()
                        + " está cerrado; no admite movimientos del " + date + ".");
            }
            return p;
        }

        YearMonth ym = YearMonth.from(date);
        AccountingPeriod fresh = new AccountingPeriod();
        fresh.setCompanyId(companyId);
        fresh.setName(nombreMes(ym));
        fresh.setStartDate(ym.atDay(1));
        fresh.setEndDate(ym.atEndOfMonth());
        fresh.setStatus("open");
        return periods.save(fresh);
    }

    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

    private static String nombreMes(YearMonth ym) {
        return MESES[ym.getMonthValue() - 1] + " " + ym.getYear();
    }

    private static boolean sig(BigDecimal v) {
        return v != null && v.signum() != 0;
    }

    private static BigDecimal round(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Azúcar para armar la lista sin repetir `new ArrayList<>()` en cada llamador. */
    public static List<Line> lines(Line... ls) {
        List<Line> out = new ArrayList<>();
        for (Line l : ls) if (l != null) out.add(l);
        return out;
    }
}
