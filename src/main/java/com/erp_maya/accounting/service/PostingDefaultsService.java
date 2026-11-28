package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.domain.JournalTemplate;
import com.erp_maya.accounting.domain.JournalTemplateLine;
import com.erp_maya.accounting.domain.PostingRule;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.accounting.repository.JournalTemplateRepository;
import com.erp_maya.accounting.repository.PostingRuleRepository;
import com.erp_maya.settings.domain.CompanySetting;
import com.erp_maya.settings.repository.CompanySettingRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Deja a una empresa lista para contabilizar: catálogo mínimo, claves
 * posting.* y las plantillas por defecto asociadas a sus casos de uso.
 *
 * Antes solo lo hacían las migraciones 053/058, que corren una vez y no ven a
 * las empresas creadas después desde la consola: esas nacían sin poder vender.
 * Recibe el id de la empresa en vez de leerlo del token porque la llama la
 * consola de plataforma, que no opera dentro de ninguna empresa.
 *
 * Idempotente: no toca nada que ya exista.
 */
@Singleton
public class PostingDefaultsService {

    private final AccountRepository accounts;
    private final CompanySettingRepository settings;
    private final JournalTemplateRepository templates;
    private final PostingRuleRepository rules;

    public PostingDefaultsService(AccountRepository accounts, CompanySettingRepository settings,
                                  JournalTemplateRepository templates, PostingRuleRepository rules) {
        this.accounts = accounts;
        this.settings = settings;
        this.templates = templates;
        this.rules = rules;
    }

    @Transactional
    public void seed(Long companyId) {
        // Primero los niveles 1 a 3, de arriba hacia abajo: el árbol tiene que
        // estar completo (AccountTreeRules) y cada nivel cuelga del anterior.
        Map<String, Account> grupos = new HashMap<>();
        for (DefaultPostingTemplates.DefaultGroup g : DefaultPostingTemplates.GROUPS) {
            Account padre = grupos.get(DefaultPostingTemplates.parentCode(g.code()));
            Account a = accounts.findByCompanyIdAndCode(companyId, g.code()).orElseGet(() -> {
                Account n = new Account();
                n.setCompanyId(companyId);
                n.setCode(g.code());
                n.setName(g.name());
                n.setLevel(g.level());
                n.setNormalBalance(g.normalBalance());
                n.setAllowsEntries(Boolean.FALSE);
                n.setParent(padre);
                return accounts.save(n);
            });
            grupos.put(g.code(), a);
        }

        Map<String, Account> porCodigo = new HashMap<>();
        for (DefaultPostingTemplates.DefaultAccount d : DefaultPostingTemplates.ACCOUNTS) {
            Account padre = grupos.get(DefaultPostingTemplates.parentCode(d.code()));
            Account a = accounts.findByCompanyIdAndCode(companyId, d.code()).orElseGet(() -> {
                Account n = new Account();
                n.setCompanyId(companyId);
                n.setCode(d.code());
                n.setName(d.name());
                n.setLevel(DefaultPostingTemplates.DETAIL_LEVEL);
                n.setNormalBalance(d.normalBalance());
                n.setAllowsEntries(Boolean.TRUE);
                n.setParent(padre);
                return accounts.save(n);
            });
            // Una cuenta sembrada antes de que el árbol fuera obligatorio no
            // tenía padre: se le pone.
            if (a.getParent() == null && padre != null && Integer.valueOf(DefaultPostingTemplates.DETAIL_LEVEL).equals(a.getLevel())) {
                a.setParent(padre);
                a = accounts.update(a);
            }
            porCodigo.put(d.code(), a);

            // Las claves posting.* siguen sirviendo de respaldo (p. ej. el
            // banco de un cobro cuando su cuenta bancaria no tiene cuenta contable).
            if (settings.findByCompanyIdAndSettingKey(companyId, d.postingKey()).isEmpty()) {
                CompanySetting s = new CompanySetting();
                s.setCompanyId(companyId);
                s.setSettingKey(d.postingKey());
                s.setSettingValue(String.valueOf(a.getId()));
                settings.save(s);
            }
        }

        for (DefaultPostingTemplates.DefaultTemplate d : DefaultPostingTemplates.TEMPLATES) {
            String caso = d.useCase().code();
            if (templates.existsByCompanyIdAndUseCase(companyId, caso)) continue;

            JournalTemplate t = new JournalTemplate();
            t.setCompanyId(companyId);
            t.setUseCase(caso);
            t.setName(d.name());
            t.setDescription(d.description());
            t.setActive(Boolean.TRUE);
            t.setVersion(1);
            int pos = 0;
            for (DefaultPostingTemplates.DefaultLine l : d.lines()) {
                JournalTemplateLine line = new JournalTemplateLine();
                line.setPosition(++pos);
                line.setSide(l.side());
                line.setAccount(porCodigo.get(l.accountCode()));
                line.setUsesDocumentAccount(l.usesDocumentAccount());
                line.setAmounts(String.join(",", l.amounts()));
                line.setDescription(l.description());
                line.setUseCostCenter(l.useCostCenter());
                t.addLine(line);
            }
            t = templates.save(t);

            if (rules.findByCompanyIdAndUseCase(companyId, caso).isEmpty()) {
                PostingRule r = new PostingRule();
                r.setCompanyId(companyId);
                r.setUseCase(caso);
                r.setTemplate(t);
                r.setActive(Boolean.TRUE);
                rules.save(r);
            }
        }
    }
}
