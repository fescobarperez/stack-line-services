package com.erp_maya.receivable.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.receivable.domain.Payment;
import com.erp_maya.receivable.dto.PaymentDtos;
import com.erp_maya.receivable.repository.PaymentRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Singleton
public class PaymentService {

    private final PaymentRepository payments;
    private final ClientRepository clients;
    private final SaleRepository sales;
    private final TenantContext tenant;

    public PaymentService(PaymentRepository payments, ClientRepository clients,
                          SaleRepository sales, TenantContext tenant) {
        this.payments = payments;
        this.clients = clients;
        this.sales = sales;
        this.tenant = tenant;
    }

    @Transactional
    public Page<PaymentDtos.Response> list(Long clientId, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        if (clientId != null) {
            List<Payment> rows = payments.findByCompanyIdAndClientId(companyId, clientId);
            return Page.of(rows.stream().map(PaymentService::toResponse).toList(), pageable, (long) rows.size());
        }
        return payments.findByCompanyIdOrderByPaymentDateDesc(companyId, pageable)
                .map(PaymentService::toResponse);
    }

    @Transactional
    public PaymentDtos.Response get(Long id) {
        return toResponse(payments.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Pago " + id + " no encontrado")));
    }

    @Transactional
    public PaymentDtos.Response create(PaymentDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        Client client = clients.findByIdAndCompanyId(req.clientId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente " + req.clientId() + " no encontrado"));

        Payment p = new Payment();
        p.setCompanyId(companyId);
        p.setClient(client);
        p.setAmount(req.amount());
        p.setPaymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now());
        p.setMethod(req.method());
        p.setReference(req.reference());
        p.setNotes(req.notes());
        if (req.saleId() != null) {
            sales.findByIdAndCompanyId(req.saleId(), companyId).ifPresent(p::setSale);
        }
        Payment saved = payments.save(p);

        // El abono baja el saldo por cobrar del cliente.
        BigDecimal balance = client.getBalance() != null ? client.getBalance() : BigDecimal.ZERO;
        client.setBalance(balance.subtract(req.amount()));

        return toResponse(saved);
    }

    private static PaymentDtos.Response toResponse(Payment p) {
        return new PaymentDtos.Response(p.getId(),
                p.getClient() != null ? p.getClient().getId() : null,
                p.getClient() != null ? p.getClient().getName() : null,
                p.getSale() != null ? p.getSale().getId() : null,
                p.getAmount(), p.getPaymentDate(), p.getMethod(), p.getReference(), p.getNotes());
    }
}
