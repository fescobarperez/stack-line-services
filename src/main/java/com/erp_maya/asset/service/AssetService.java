package com.erp_maya.asset.service;

import com.erp_maya.accounting.domain.Account;
import com.erp_maya.accounting.domain.JournalEntry;
import com.erp_maya.accounting.domain.JournalEntryLine;
import com.erp_maya.accounting.repository.AccountRepository;
import com.erp_maya.accounting.repository.JournalEntryRepository;
import com.erp_maya.asset.domain.AssetDepreciation;
import com.erp_maya.asset.domain.FixedAsset;
import com.erp_maya.asset.dto.AssetDtos;
import com.erp_maya.asset.repository.AssetDepreciationRepository;
import com.erp_maya.asset.repository.FixedAssetRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.settings.domain.CompanySetting;
import com.erp_maya.settings.repository.CompanySettingRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Activos fijos y su depreciación (línea recta mensual). */
@Singleton
public class AssetService {

    private static final BigDecimal TWELVE = new BigDecimal("12");

    // Claves de configuración de las cuentas de la partida de baja (company_settings).
    public static final String KEY_ASSET_ACCOUNT = "asset_disposal.asset_account";
    public static final String KEY_DEPR_ACCOUNT  = "asset_disposal.depreciation_account";
    public static final String KEY_LOSS_ACCOUNT  = "asset_disposal.loss_account";

    private final FixedAssetRepository assets;
    private final AssetDepreciationRepository depreciations;
    private final BranchRepository branches;
    private final AccountRepository accounts;
    private final JournalEntryRepository journalEntries;
    private final CompanySettingRepository settings;
    private final TenantContext tenant;

    public AssetService(FixedAssetRepository assets, AssetDepreciationRepository depreciations,
                        BranchRepository branches, AccountRepository accounts,
                        JournalEntryRepository journalEntries, CompanySettingRepository settings,
                        TenantContext tenant) {
        this.assets = assets;
        this.depreciations = depreciations;
        this.branches = branches;
        this.accounts = accounts;
        this.journalEntries = journalEntries;
        this.settings = settings;
        this.tenant = tenant;
    }

    @Transactional
    public List<AssetDtos.Response> list() {
        return assets.findByCompanyId(tenant.getCompanyId()).stream().map(AssetService::toResponse).toList();
    }

    @Transactional
    public AssetDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public List<AssetDtos.DepreciationResponse> depreciationHistory(Long id) {
        find(id);
        return depreciations.findByCompanyIdAndFixedAssetIdOrderByPeriodDateDesc(tenant.getCompanyId(), id)
                .stream().map(d -> new AssetDtos.DepreciationResponse(d.getId(), d.getPeriodDate(),
                        d.getDepreciationAmount(), d.getAccumulatedAmount(), d.getBookValue())).toList();
    }

    @Transactional
    public AssetDtos.Response create(AssetDtos.Request req) {
        FixedAsset a = new FixedAsset();
        a.setCompanyId(tenant.getCompanyId());
        apply(a, req);
        a.setAccumulatedDepreciation(BigDecimal.ZERO);
        a.setBookValue(a.getPurchaseCost());
        return toResponse(assets.save(a));
    }

    @Transactional
    public AssetDtos.Response update(Long id, AssetDtos.Request req) {
        FixedAsset a = find(id);
        apply(a, req);
        return toResponse(assets.update(a));
    }

    @Transactional
    public void delete(Long id) {
        assets.delete(find(id));
    }

    /**
     * Da de baja el activo y genera automáticamente la póliza de disposición
     * usando las cuentas CONFIGURADAS (company_settings):
     *   Dr depreciación acumulada + Dr pérdida en libros = Cr costo del activo.
     */
    @Transactional
    public AssetDtos.Response dispose(Long id, AssetDtos.DisposeRequest req) {
        Long companyId = tenant.getCompanyId();
        FixedAsset a = find(id);
        if ("baja".equals(a.getStatus())) {
            throw new IllegalStateException("El activo ya está dado de baja.");
        }
        LocalDate date = req != null && req.disposalDate() != null ? req.disposalDate() : LocalDate.now();
        BigDecimal cost = a.getPurchaseCost() != null ? a.getPurchaseCost() : BigDecimal.ZERO;
        BigDecimal accum = a.getAccumulatedDepreciation() != null ? a.getAccumulatedDepreciation() : BigDecimal.ZERO;
        BigDecimal book = cost.subtract(accum);

        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            Account assetAcc = configuredAccount(KEY_ASSET_ACCOUNT, "activo fijo (costo)", companyId);
            Account deprAcc = configuredAccount(KEY_DEPR_ACCOUNT, "depreciación acumulada", companyId);
            Account lossAcc = configuredAccount(KEY_LOSS_ACCOUNT, "pérdida en baja de activos", companyId);

            JournalEntry entry = new JournalEntry();
            entry.setCompanyId(companyId);
            entry.setEntryDate(date);
            entry.setEntryType("auto");
            entry.setDescription("Baja de activo fijo " + a.getAssetCode() + " · " + a.getName());
            entry.setReference(a.getAssetCode());
            entry.setSourceType("asset_disposal");

            if (accum.compareTo(BigDecimal.ZERO) > 0) entry.addLine(line(deprAcc, accum, BigDecimal.ZERO));
            if (book.compareTo(BigDecimal.ZERO) > 0)  entry.addLine(line(lossAcc, book, BigDecimal.ZERO));
            entry.addLine(line(assetAcc, BigDecimal.ZERO, cost));

            entry.setTotalDebit(accum.add(book));
            entry.setTotalCredit(cost);
            entry.setStatus("posted");
            JournalEntry saved = journalEntries.save(entry);
            a.setDisposalJournalEntryId(saved.getId());
        }

        a.setStatus("baja");
        a.setDisposalDate(date);
        if (req != null && req.notes() != null && !req.notes().isBlank()) a.setNotes(req.notes());
        return toResponse(assets.update(a));
    }

    /** Resuelve la cuenta configurada para una clave; error claro si falta. */
    private Account configuredAccount(String key, String label, Long companyId) {
        CompanySetting s = settings.findByCompanyIdAndSettingKey(companyId, key)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay cuenta configurada para " + label + ". Configúrala en Activos fijos → Configuración contable."));
        Long accountId;
        try {
            accountId = Long.parseLong(s.getSettingValue().trim());
        } catch (Exception e) {
            throw new IllegalStateException("La cuenta configurada para " + label + " no es válida.");
        }
        return accounts.findByIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta configurada (" + label + ") no encontrada: " + accountId));
    }

    private JournalEntryLine line(Account account, BigDecimal debit, BigDecimal credit) {
        JournalEntryLine l = new JournalEntryLine();
        l.setAccount(account);
        l.setDebit(debit);
        l.setCredit(credit);
        return l;
    }

    /** Registra una cuota de depreciación mensual (costo × tasa anual / 12). */
    @Transactional
    public AssetDtos.DepreciationResponse depreciate(Long id, LocalDate periodDate) {
        FixedAsset a = find(id);
        BigDecimal rate = a.getDepreciationRate() != null ? a.getDepreciationRate() : BigDecimal.ZERO;
        BigDecimal monthly = a.getPurchaseCost().multiply(rate).divide(TWELVE, 2, RoundingMode.HALF_UP);

        BigDecimal accumulated = a.getAccumulatedDepreciation().add(monthly);
        if (accumulated.compareTo(a.getPurchaseCost()) > 0) {
            accumulated = a.getPurchaseCost();
            monthly = accumulated.subtract(a.getAccumulatedDepreciation());
        }
        BigDecimal book = a.getPurchaseCost().subtract(accumulated);
        a.setAccumulatedDepreciation(accumulated);
        a.setBookValue(book);
        assets.update(a);

        AssetDepreciation dep = new AssetDepreciation();
        dep.setCompanyId(tenant.getCompanyId());
        dep.setFixedAsset(a);
        dep.setPeriodDate(periodDate != null ? periodDate : LocalDate.now());
        dep.setDepreciationAmount(monthly);
        dep.setAccumulatedAmount(accumulated);
        dep.setBookValue(book);
        AssetDepreciation saved = depreciations.save(dep);
        return new AssetDtos.DepreciationResponse(saved.getId(), saved.getPeriodDate(),
                saved.getDepreciationAmount(), saved.getAccumulatedAmount(), saved.getBookValue());
    }

    private void apply(FixedAsset a, AssetDtos.Request req) {
        a.setAssetCode(req.assetCode());
        a.setName(req.name());
        a.setCategory(req.category());
        a.setPurchaseCost(req.purchaseCost() != null ? req.purchaseCost() : BigDecimal.ZERO);
        a.setAcquiredDate(req.acquiredDate());
        a.setSerial(req.serial());
        a.setStatus(req.status() != null ? req.status() : "active");
        a.setDepreciationRate(req.depreciationRate());
        a.setUsefulLifeYears(req.usefulLifeYears());
        a.setNotes(req.notes());
        a.setBranch(req.branchId() == null ? null
                : branches.findByIdAndCompanyId(req.branchId(), tenant.getCompanyId()).orElse(null));
    }

    private FixedAsset find(Long id) {
        return assets.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Activo fijo " + id + " no encontrado"));
    }

    private static AssetDtos.Response toResponse(FixedAsset a) {
        return new AssetDtos.Response(a.getId(), a.getAssetCode(), a.getName(), a.getCategory(),
                a.getPurchaseCost(), a.getAcquiredDate(), a.getSerial(),
                a.getBranch() != null ? a.getBranch().getId() : null, a.getStatus(),
                a.getDepreciationRate(), a.getUsefulLifeYears(), a.getAccumulatedDepreciation(),
                a.getBookValue(), a.getDisposalDate(), a.getDisposalJournalEntryId(), a.getNotes());
    }
}
