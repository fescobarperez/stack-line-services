package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.domain.JournalTemplate;
import com.erp_maya.accounting.domain.JournalTemplateLine;
import com.erp_maya.accounting.domain.PostingRule;
import com.erp_maya.accounting.dto.JournalTemplateDtos;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.accounting.repository.JournalEntryRepository;
import com.erp_maya.accounting.repository.JournalTemplateRepository;
import com.erp_maya.accounting.repository.PostingRuleRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plantillas de partidas (Contabilidad → Partidas automáticas).
 *
 * Una plantilla se valida al guardarla con el ejemplo de su caso de uso: si no
 * cuadra ahí, no cuadraría con ninguna venta real, y es mejor decirlo al
 * configurarla que en medio de una venta.
 */
@Singleton
public class JournalTemplateService {

    private final JournalTemplateRepository templates;
    private final PostingRuleRepository rules;
    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final TenantContext tenant;

    public JournalTemplateService(JournalTemplateRepository templates, PostingRuleRepository rules,
                                  AccountRepository accounts, JournalEntryRepository entries,
                                  TenantContext tenant) {
        this.templates = templates;
        this.rules = rules;
        this.accounts = accounts;
        this.entries = entries;
        this.tenant = tenant;
    }

    /** El catálogo de casos de uso, para armar el editor. */
    public List<JournalTemplateDtos.UseCaseResponse> useCases() {
        return Arrays.stream(PostingCase.values()).map(c -> new JournalTemplateDtos.UseCaseResponse(
                c.code(), c.label(), c.group(),
                c.amounts().stream().map(a -> new JournalTemplateDtos.AmountInfo(a.key(), a.label())).toList(),
                c.documentAccount(), new LinkedHashMap<>(c.example()))).toList();
    }

    @Transactional
    public List<JournalTemplateDtos.TemplateResponse> list() {
        Long companyId = tenant.getCompanyId();
        Map<Long, String> asociadas = asociaciones(companyId);
        return templates.findByCompanyId(companyId).stream()
                .sorted(Comparator.comparing(JournalTemplate::getUseCase).thenComparing(JournalTemplate::getName))
                .map(t -> toResponse(t, asociadas)).toList();
    }

    @Transactional
    public JournalTemplateDtos.TemplateResponse get(Long id) {
        return toResponse(find(id), asociaciones(tenant.getCompanyId()));
    }

    @Transactional
    public JournalTemplateDtos.TemplateResponse create(JournalTemplateDtos.TemplateRequest req) {
        PostingCase useCase = useCase(req.useCase());
        Map<Long, Account> cuentas = validar(useCase, req);

        JournalTemplate t = new JournalTemplate();
        t.setCompanyId(tenant.getCompanyId());
        t.setUseCase(useCase.code());
        apply(t, req, cuentas);
        t.setVersion(1);
        t = templates.save(t);
        return toResponse(t, asociaciones(tenant.getCompanyId()));
    }

    @Transactional
    public JournalTemplateDtos.TemplateResponse update(Long id, JournalTemplateDtos.TemplateRequest req) {
        JournalTemplate t = find(id);
        PostingCase useCase = useCase(req.useCase());
        Map<Long, String> asociadas = asociaciones(t.getCompanyId());
        String asociadaA = asociadas.get(t.getId());

        // Una plantilla asociada sostiene una operación: cambiarle el caso de
        // uso o apagarla dejaría esa operación bloqueada sin que nadie lo note.
        if (asociadaA != null && !useCase.code().equals(t.getUseCase())) {
            throw new IllegalStateException("La plantilla está asociada a «" + asociadaA
                    + "»; no se le puede cambiar el caso de uso. Crea otra plantilla.");
        }
        if (asociadaA != null && Boolean.FALSE.equals(req.active())) {
            throw new IllegalStateException("La plantilla está asociada a «" + asociadaA
                    + "». Asocia otra plantilla antes de desactivarla.");
        }

        Map<Long, Account> cuentas = validar(useCase, req);
        t.setUseCase(useCase.code());
        t.getLines().clear();
        apply(t, req, cuentas);
        t.setVersion(t.getVersion() == null ? 1 : t.getVersion() + 1);
        return toResponse(templates.update(t), asociadas);
    }

    @Transactional
    public void delete(Long id) {
        JournalTemplate t = find(id);
        String asociadaA = asociaciones(t.getCompanyId()).get(t.getId());
        if (asociadaA != null || rules.existsByTemplateId(t.getId())) {
            throw new IllegalStateException("La plantilla está asociada"
                    + (asociadaA != null ? " a «" + asociadaA + "»" : "")
                    + ". Asocia otra plantilla antes de borrarla.");
        }
        // Las partidas guardan con qué plantilla nacieron: borrarla les
        // quitaría el rastro. Se desactiva en su lugar.
        if (entries.existsByTemplateId(t.getId())) {
            throw new IllegalStateException("La plantilla ya generó partidas; desactívala en vez de borrarla.");
        }
        templates.delete(t);
    }

    /**
     * La partida que daría la plantilla con montos de ejemplo (o los que se
     * manden). No lanza por errores de configuración: los devuelve, para que
     * el editor los muestre mientras se arma la plantilla.
     */
    @Transactional
    public JournalTemplateDtos.PreviewResponse preview(JournalTemplateDtos.PreviewRequest req) {
        PostingCase useCase = useCase(req.template().useCase());
        Map<String, BigDecimal> montos = req.amounts() == null || req.amounts().isEmpty()
                ? new LinkedHashMap<>(useCase.example()) : req.amounts();

        List<TemplateEvaluator.LineSpec> specs = specs(req.template());
        List<String> errores = new ArrayList<>(TemplateEvaluator.validate(useCase, specs));
        Map<Long, Account> cuentas = cuentasDe(req.template(), errores);

        List<JournalTemplateDtos.PreviewLine> lineas = new ArrayList<>();
        BigDecimal debe = BigDecimal.ZERO, haber = BigDecimal.ZERO;
        var ctx = new TemplateEvaluator.Context(montos, null, null, "Venta F-0001", "F-0001", "Cliente de ejemplo");
        for (TemplateEvaluator.LineSpec l : specs) {
            BigDecimal monto = TemplateEvaluator.amount(l, montos).setScale(2, RoundingMode.HALF_UP);
            boolean esDebe = TemplateEvaluator.DEBIT.equals(l.side());
            Account a = l.accountId() == null ? null : cuentas.get(l.accountId());
            lineas.add(new JournalTemplateDtos.PreviewLine(l.side(),
                    a != null ? a.getCode() : null, a != null ? a.getName() : null,
                    l.usesDocumentAccount(),
                    esDebe ? monto : BigDecimal.ZERO, esDebe ? BigDecimal.ZERO : monto,
                    TemplateEvaluator.text(l.description(), ctx)));
            if (esDebe) debe = debe.add(monto); else haber = haber.add(monto);
        }
        return new JournalTemplateDtos.PreviewResponse(lineas, debe, haber,
                debe.compareTo(haber) == 0, errores, montos);
    }

    // ── Interno ──────────────────────────────────────────────────────────

    private JournalTemplate find(Long id) {
        return templates.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla " + id + " no encontrada"));
    }

    private static PostingCase useCase(String code) {
        return PostingCase.byCode(code).orElseThrow(() ->
                new IllegalStateException("Caso de uso desconocido: «" + code + "»."));
    }

    /** id de plantilla → nombre del caso de uso donde está asociada. */
    private Map<Long, String> asociaciones(Long companyId) {
        Map<Long, String> out = new HashMap<>();
        for (PostingRule r : rules.findByCompanyId(companyId)) {
            String nombre = PostingCase.byCode(r.getUseCase()).map(PostingCase::label).orElse(r.getUseCase());
            out.put(r.getTemplate().getId(), nombre);
        }
        return out;
    }

    /** Valida la plantilla completa y devuelve sus cuentas por id. Lanza con todos los errores juntos. */
    private Map<Long, Account> validar(PostingCase useCase, JournalTemplateDtos.TemplateRequest req) {
        List<String> errores = new ArrayList<>(TemplateEvaluator.validate(useCase, specs(req)));
        Map<Long, Account> cuentas = cuentasDe(req, errores);
        if (!errores.isEmpty()) throw new IllegalStateException(String.join(" ", errores));
        return cuentas;
    }

    /** Las cuentas de la plantilla, de la empresa y de detalle. Lo que no cumpla va a `errores`. */
    private Map<Long, Account> cuentasDe(JournalTemplateDtos.TemplateRequest req, List<String> errores) {
        Long companyId = tenant.getCompanyId();
        Map<Long, Account> out = new HashMap<>();
        if (req.lines() == null) return out;
        int n = 0;
        for (JournalTemplateDtos.LineRequest l : req.lines()) {
            n++;
            if (l.accountId() == null || out.containsKey(l.accountId())) continue;
            var cuenta = accounts.findByIdAndCompanyId(l.accountId(), companyId);
            if (cuenta.isEmpty()) {
                errores.add("Línea " + n + ": la cuenta no existe.");
            } else if (Boolean.FALSE.equals(cuenta.get().getAllowsEntries())) {
                errores.add("Línea " + n + ": la cuenta " + cuenta.get().getCode()
                        + " es de agrupación; elige una cuenta de detalle.");
            } else {
                out.put(l.accountId(), cuenta.get());
            }
        }
        return out;
    }

    private static List<TemplateEvaluator.LineSpec> specs(JournalTemplateDtos.TemplateRequest req) {
        if (req.lines() == null) return List.of();
        return req.lines().stream().map(l -> new TemplateEvaluator.LineSpec(
                l.side(), l.accountId(), Boolean.TRUE.equals(l.usesDocumentAccount()),
                l.amounts() == null ? List.of() : l.amounts().stream().map(String::trim).toList(),
                l.description(), !Boolean.FALSE.equals(l.useCostCenter()))).toList();
    }

    private static void apply(JournalTemplate t, JournalTemplateDtos.TemplateRequest req, Map<Long, Account> cuentas) {
        t.setName(req.name().trim());
        t.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        t.setActive(!Boolean.FALSE.equals(req.active()));
        int pos = 0;
        for (JournalTemplateDtos.LineRequest l : req.lines()) {
            JournalTemplateLine line = new JournalTemplateLine();
            line.setPosition(++pos);
            line.setSide(l.side());
            line.setAccount(l.accountId() == null ? null : cuentas.get(l.accountId()));
            line.setUsesDocumentAccount(Boolean.TRUE.equals(l.usesDocumentAccount()));
            line.setAmounts(String.join(",", l.amounts().stream().map(String::trim).toList()));
            line.setDescription(l.description() == null || l.description().isBlank() ? null : l.description().trim());
            line.setUseCostCenter(!Boolean.FALSE.equals(l.useCostCenter()));
            t.addLine(line);
        }
    }

    private JournalTemplateDtos.TemplateResponse toResponse(JournalTemplate t, Map<Long, String> asociadas) {
        String nombreCaso = PostingCase.byCode(t.getUseCase()).map(PostingCase::label).orElse(t.getUseCase());
        List<JournalTemplateDtos.LineResponse> lineas = t.getLines().stream().map(l -> {
            Account a = l.getAccount();
            return new JournalTemplateDtos.LineResponse(l.getId(), l.getPosition(), l.getSide(),
                    a != null ? a.getId() : null, a != null ? a.getCode() : null, a != null ? a.getName() : null,
                    l.getUsesDocumentAccount(), l.amountKeys(), l.getDescription(), l.getUseCostCenter());
        }).toList();
        boolean usada = t.getId() != null && entries.existsByTemplateId(t.getId());
        return new JournalTemplateDtos.TemplateResponse(t.getId(), t.getUseCase(), nombreCaso, t.getName(),
                t.getDescription(), t.getActive(), t.getVersion(), asociadas.get(t.getId()), usada, lineas);
    }
}
