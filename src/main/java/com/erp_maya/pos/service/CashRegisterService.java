package com.erp_maya.pos.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.pos.domain.CashPoint;
import com.erp_maya.pos.domain.CashRegister;
import com.erp_maya.pos.dto.CashRegisterDtos;
import com.erp_maya.pos.repository.CashPointRepository;
import com.erp_maya.pos.repository.CashRegisterRepository;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Turnos de caja.
 *
 * Reglas de uso, todas verificadas aquí y respaldadas por índices únicos
 * parciales en la base (ver migración 033):
 *
 *   1. Una caja solo puede tener un turno abierto a la vez.
 *   2. Un cajero solo puede tener un turno abierto a la vez.
 *   3. No se abre turno si esa caja arrastra uno de una fecha operativa
 *      anterior sin cerrar: primero se cuadra el día pendiente.
 *   4. Solo se cierra un turno que esté abierto.
 */
@Singleton
public class CashRegisterService {

    private static final String OPEN = "open";

    private final CashRegisterRepository registers;
    private final CashPointRepository points;
    private final UserRepository users;
    private final TenantContext tenant;

    public CashRegisterService(CashRegisterRepository registers, CashPointRepository points,
                               UserRepository users, TenantContext tenant) {
        this.registers = registers;
        this.points = points;
        this.users = users;
        this.tenant = tenant;
    }

    @Transactional
    public List<CashRegisterDtos.Response> list(String status) {
        Long companyId = tenant.getCompanyId();
        List<CashRegister> rows = (status != null && !status.isBlank())
                ? registers.findByCompanyIdAndStatus(companyId, status)
                : registers.findByCompanyId(companyId);
        return rows.stream().map(CashRegisterService::toResponse).toList();
    }

    @Transactional
    public CashRegisterDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    /** Turnos abiertos de días anteriores. El frontend los usa para avisar al entrar. */
    @Transactional
    public List<CashRegisterDtos.Response> pending() {
        return registers.findByCompanyIdAndStatusAndBusinessDateLessThan(
                        tenant.getCompanyId(), OPEN, LocalDate.now())
                .stream().map(CashRegisterService::toResponse).toList();
    }

    @Transactional
    public CashRegisterDtos.Response open(CashRegisterDtos.OpenRequest req) {
        Long companyId = tenant.getCompanyId();
        CashPoint point = points.findByIdAndCompanyId(req.cashPointId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Caja " + req.cashPointId() + " no encontrada"));
        if (!"active".equals(point.getStatus())) {
            throw new IllegalStateException("La caja " + point.getCode() + " está inactiva");
        }

        // (1) y (3): la caja no puede estar ocupada, ni arrastrar un día sin cuadrar.
        registers.findByCompanyIdAndCashPointIdAndStatus(companyId, point.getId(), OPEN)
                .ifPresent(prev -> {
                    String quien = prev.getUser() != null ? prev.getUser().getName() : "otro usuario";
                    if (prev.getBusinessDate() != null && prev.getBusinessDate().isBefore(LocalDate.now())) {
                        throw new IllegalStateException(
                                "La caja " + point.getCode() + " tiene el turno del " + prev.getBusinessDate()
                                        + " sin cerrar (" + quien + "). Ciérralo con el arqueo de ese día antes de abrir.");
                    }
                    throw new IllegalStateException(
                            "La caja " + point.getCode() + " ya está abierta por " + quien);
                });

        // (2): el cajero no puede estar en dos cajas a la vez.
        User user = null;
        if (req.userId() != null) {
            user = users.findByIdAndCompanyId(req.userId(), companyId).orElse(null);
            registers.findByCompanyIdAndUserIdAndStatus(companyId, req.userId(), OPEN)
                    .ifPresent(other -> {
                        CashPoint p = other.getCashPoint();
                        throw new IllegalStateException("Ya tienes abierta la caja "
                                + (p != null ? p.getCode() : other.getId()) + "; ciérrala antes de abrir otra.");
                    });
        }

        CashRegister r = new CashRegister();
        r.setCompanyId(companyId);
        r.setCashPoint(point);
        r.setBranch(point.getBranch());
        r.setUser(user);
        r.setBusinessDate(LocalDate.now());
        r.setOpeningAmount(req.openingAmount() != null ? req.openingAmount() : BigDecimal.ZERO);
        r.setOpenedAt(Instant.now());
        r.setStatus(OPEN);
        return toResponse(registers.save(r));
    }

    @Transactional
    public CashRegisterDtos.Response close(Long id, CashRegisterDtos.CloseRequest req) {
        CashRegister r = find(id);
        // (4): cerrar dos veces recalculaba el descuadre y pisaba closed_at.
        if (!OPEN.equals(r.getStatus())) {
            throw new IllegalStateException("El turno " + id + " ya está " + r.getStatus());
        }
        BigDecimal expectedCash = r.getOpeningAmount().add(r.getSalesCash()).subtract(r.getRefunds());
        r.setClosingAmount(req.closingAmount());
        r.setDifference(req.closingAmount().subtract(expectedCash));
        r.setClosedAt(Instant.now());
        r.setStatus("closed");
        return toResponse(registers.update(r));
    }

    private CashRegister find(Long id) {
        return registers.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Turno " + id + " no encontrado"));
    }

    private static CashRegisterDtos.Response toResponse(CashRegister r) {
        Branch b = r.getBranch();
        CashPoint p = r.getCashPoint();
        User u = r.getUser();
        return new CashRegisterDtos.Response(r.getId(),
                b != null ? b.getId() : null, b != null ? b.getName() : null,
                p != null ? p.getId() : null, p != null ? p.getCode() : null, p != null ? p.getName() : null,
                r.getBusinessDate(),
                u != null ? u.getId() : null, u != null ? u.getName() : null,
                r.getOpenedAt(), r.getClosedAt(), r.getOpeningAmount(), r.getClosingAmount(),
                r.getSalesTotal(), r.getSalesCash(), r.getSalesCard(), r.getRefunds(),
                r.getDifference(), r.getStatus());
    }
}
