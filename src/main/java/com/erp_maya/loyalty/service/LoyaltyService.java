package com.erp_maya.loyalty.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.loyalty.domain.LoyaltyAccount;
import com.erp_maya.loyalty.domain.LoyaltyMovement;
import com.erp_maya.loyalty.dto.LoyaltyDtos;
import com.erp_maya.loyalty.repository.LoyaltyAccountRepository;
import com.erp_maya.loyalty.repository.LoyaltyMovementRepository;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.pos.repository.SaleRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Programa de lealtad: cuentas de puntos y sus movimientos. */
@Singleton
public class LoyaltyService {

    private final LoyaltyAccountRepository accounts;
    private final LoyaltyMovementRepository movements;
    private final ClientRepository clients;
    private final SaleRepository sales;
    private final TenantContext tenant;

    public LoyaltyService(LoyaltyAccountRepository accounts, LoyaltyMovementRepository movements,
                          ClientRepository clients, SaleRepository sales, TenantContext tenant) {
        this.accounts = accounts;
        this.movements = movements;
        this.clients = clients;
        this.sales = sales;
        this.tenant = tenant;
    }

    @Transactional
    public List<LoyaltyDtos.AccountResponse> list() {
        return accounts.findByCompanyId(tenant.getCompanyId()).stream().map(LoyaltyService::toAccount).toList();
    }

    @Transactional
    public LoyaltyDtos.AccountResponse get(Long id) {
        return toAccount(find(id));
    }

    @Transactional
    public LoyaltyDtos.AccountResponse create(LoyaltyDtos.AccountRequest req) {
        LoyaltyAccount a = new LoyaltyAccount();
        a.setCompanyId(tenant.getCompanyId());
        a.setMemberCode(req.memberCode());
        a.setName(req.name());
        a.setNit(req.nit());
        a.setPhone(req.phone());
        a.setEmail(req.email());
        a.setTier(req.tier() != null ? req.tier() : "basico");
        a.setJoinDate(req.joinDate() != null ? req.joinDate() : LocalDate.now());
        if (req.clientId() != null) {
            clients.findByIdAndCompanyId(req.clientId(), tenant.getCompanyId()).ifPresent(a::setClient);
        }
        return toAccount(accounts.save(a));
    }

    @Transactional
    public List<LoyaltyDtos.MovementResponse> movements(Long accountId) {
        find(accountId);
        return movements.findByCompanyIdAndAccountIdOrderByIdDesc(tenant.getCompanyId(), accountId)
                .stream().map(LoyaltyService::toMovement).toList();
    }

    /** Registra un movimiento y actualiza el saldo de puntos de la cuenta. */
    @Transactional
    public LoyaltyDtos.MovementResponse addMovement(Long accountId, LoyaltyDtos.MovementRequest req) {
        LoyaltyAccount account = find(accountId);

        LoyaltyMovement m = new LoyaltyMovement();
        m.setCompanyId(tenant.getCompanyId());
        m.setAccount(account);
        m.setMovementType(req.movementType());
        m.setPoints(req.points());
        m.setReference(req.reference());
        m.setAmount(req.amount() != null ? req.amount() : BigDecimal.ZERO);
        m.setMovementDate(LocalDate.now());
        if (req.saleId() != null) {
            sales.findByIdAndCompanyId(req.saleId(), tenant.getCompanyId()).ifPresent(m::setSale);
        }
        LoyaltyMovement saved = movements.save(m);

        account.setPointsBalance(account.getPointsBalance() + req.points());
        if (req.points() >= 0) {
            account.setPointsEarned(account.getPointsEarned() + req.points());
        } else {
            account.setPointsRedeemed(account.getPointsRedeemed() - req.points());
        }
        return toMovement(saved);
    }

    private LoyaltyAccount find(Long id) {
        return accounts.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta de lealtad " + id + " no encontrada"));
    }

    private static LoyaltyDtos.AccountResponse toAccount(LoyaltyAccount a) {
        return new LoyaltyDtos.AccountResponse(a.getId(), a.getMemberCode(),
                a.getClient() != null ? a.getClient().getId() : null, a.getName(), a.getNit(),
                a.getPhone(), a.getEmail(), a.getPointsBalance(), a.getTotalSpent(), a.getTier(),
                a.getJoinDate(), a.getLastPurchaseDate(), a.getPointsEarned(), a.getPointsRedeemed());
    }

    private static LoyaltyDtos.MovementResponse toMovement(LoyaltyMovement m) {
        return new LoyaltyDtos.MovementResponse(m.getId(),
                m.getAccount() != null ? m.getAccount().getId() : null,
                m.getMovementType(), m.getPoints(),
                m.getSale() != null ? m.getSale().getId() : null,
                m.getReference(), m.getAmount(), m.getMovementDate());
    }
}
