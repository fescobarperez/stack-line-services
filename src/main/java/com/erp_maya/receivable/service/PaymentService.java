package com.erp_maya.receivable.service;

import com.erp_maya.bank.dto.BankDtos;
import com.erp_maya.bank.service.BankService;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.receivable.domain.Payment;
import com.erp_maya.receivable.dto.PaymentDtos;
import com.erp_maya.receivable.repository.PaymentRepository;
import com.erp_maya.sequence.service.DocumentSequenceService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Singleton
public class PaymentService {

    /**
     * Métodos en los que el dinero entra a un banco y por tanto exigen cuenta.
     * Efectivo y tarjeta quedan fuera: el efectivo va a la caja y la tarjeta
     * liquida después, con comisión, por una vía que este cobro no conoce.
     */
    private static final Set<String> BANK_METHODS = Set.of("transferencia", "deposito", "depósito");

    private final PaymentRepository payments;
    private final ClientRepository clients;
    private final SaleRepository sales;
    private final DocumentSequenceService sequences;
    private final BankService banks;
    private final TenantContext tenant;

    public PaymentService(PaymentRepository payments, ClientRepository clients,
                          SaleRepository sales, DocumentSequenceService sequences,
                          BankService banks, TenantContext tenant) {
        this.sequences = sequences;
        this.banks = banks;
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
        p.setProjectId(req.projectId());

        // Sin cuenta, una transferencia entra en cuentas por cobrar pero no
        // aparece en ningún banco, y la conciliación no cuadra nunca.
        String method = req.method() != null ? req.method().trim().toLowerCase() : "";
        boolean needsBank = BANK_METHODS.contains(method);
        if (needsBank && req.bankAccountId() == null) {
            throw new IllegalStateException(
                    "Un cobro por " + method + " necesita la cuenta bancaria a la que entró el dinero.");
        }
        p.setBankAccountId(req.bankAccountId());

        // El recibo es el papel que se le entrega al cliente por este abono.
        // Se numera dentro de la misma transacción: si el cobro falla, el
        // correlativo se devuelve con el rollback y no queda un hueco.
        p.setReceiptNumber(sequences.next("RECIBO", "A"));
        Payment saved = payments.save(p);

        // El movimiento bancario es consecuencia del cobro, no un registro
        // paralelo: se crea aquí para que nadie tenga que acordarse de
        // anotarlo aparte, que es como los dos saldos terminan discrepando.
        if (needsBank) {
            var mov = banks.addMovement(req.bankAccountId(), new BankDtos.MovementRequest(
                    req.amount(), "deposit",
                    "Cobro " + saved.getReceiptNumber() + " · " + client.getName(),
                    req.reference() != null && !req.reference().isBlank()
                            ? req.reference() : saved.getReceiptNumber(),
                    saved.getPaymentDate()));
            saved.setBankMovementId(mov.id());
            payments.update(saved);
        }

        // Antes aquí se restaba `client.balance`. Ya no: el saldo se deriva en
        // v_client_balance (046). Mantener además un contador era tener dos
        // definiciones de cuentas por cobrar que se contradecían — el contador
        // solo bajaba, porque nadie lo subía al facturar.
        return toResponse(saved);
    }

    /**
     * Deja constancia de que el recibo se imprimió. No bloquea reimprimir —a
     * veces se atasca el papel— pero conserva la primera vez, que es la que
     * importa para saber si el cliente ya tiene el suyo.
     */
    @Transactional
    public PaymentDtos.Response markPrinted(Long id) {
        Payment p = payments.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Pago " + id + " no encontrado"));
        if (p.getReceiptPrintedAt() == null) {
            p.setReceiptPrintedAt(java.time.Instant.now());
            payments.update(p);
        }
        return toResponse(p);
    }

    private static PaymentDtos.Response toResponse(Payment p) {
        return new PaymentDtos.Response(p.getId(),
                p.getClient() != null ? p.getClient().getId() : null,
                p.getClient() != null ? p.getClient().getName() : null,
                p.getSale() != null ? p.getSale().getId() : null,
                p.getProjectId(),
                p.getAmount(), p.getPaymentDate(), p.getMethod(), p.getReference(), p.getNotes(),
                p.getReceiptNumber(), p.getReceiptPrintedAt(),
                p.getBankAccountId(), p.getBankMovementId());
    }
}
