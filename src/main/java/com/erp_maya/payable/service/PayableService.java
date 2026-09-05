package com.erp_maya.payable.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.Supplier;
import com.erp_maya.partner.repository.SupplierRepository;
import com.erp_maya.payable.domain.PurchaseInvoice;
import com.erp_maya.payable.domain.SupplierPayment;
import com.erp_maya.payable.dto.PayableDtos;
import com.erp_maya.payable.repository.PurchaseInvoiceRepository;
import com.erp_maya.payable.repository.SupplierPaymentRepository;
import com.erp_maya.purchasing.repository.PurchaseOrderRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Singleton
public class PayableService {

    private final PurchaseInvoiceRepository invoices;
    private final SupplierPaymentRepository paymentsRepo;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final TenantContext tenant;

    public PayableService(PurchaseInvoiceRepository invoices, SupplierPaymentRepository paymentsRepo,
                          SupplierRepository suppliers, PurchaseOrderRepository orders, TenantContext tenant) {
        this.invoices = invoices;
        this.paymentsRepo = paymentsRepo;
        this.suppliers = suppliers;
        this.orders = orders;
        this.tenant = tenant;
    }

    // ── Facturas de proveedor ─────────────────────────────────────────────
    @Transactional
    public Page<PayableDtos.InvoiceResponse> listInvoices(Pageable pageable) {
        return invoices.findByCompanyIdOrderByInvoiceDateDesc(tenant.getCompanyId(), pageable)
                .map(PayableService::toInvoice);
    }

    @Transactional
    public PayableDtos.InvoiceResponse createInvoice(PayableDtos.InvoiceRequest req) {
        Long companyId = tenant.getCompanyId();
        Supplier supplier = suppliers.findByIdAndCompanyId(req.supplierId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + req.supplierId() + " no encontrado"));

        PurchaseInvoice inv = new PurchaseInvoice();
        inv.setCompanyId(companyId);
        inv.setSupplier(supplier);
        inv.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : "FP-" + Instant.now().toEpochMilli());
        inv.setInvoiceDate(req.invoiceDate());
        inv.setDueDate(req.dueDate());
        inv.setAmount(req.amount());
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setStatus("open");
        if (req.purchaseOrderId() != null) {
            orders.findByIdAndCompanyId(req.purchaseOrderId(), companyId).ifPresent(inv::setPurchaseOrder);
        }
        PurchaseInvoice saved = invoices.save(inv);

        // Aumenta el saldo por pagar del proveedor.
        BigDecimal balance = supplier.getBalance() != null ? supplier.getBalance() : BigDecimal.ZERO;
        supplier.setBalance(balance.add(req.amount()));
        return toInvoice(saved);
    }

    // ── Pagos a proveedor ─────────────────────────────────────────────────
    @Transactional
    public Page<PayableDtos.PaymentResponse> listPayments(Pageable pageable) {
        return paymentsRepo.findByCompanyIdOrderByPaymentDateDesc(tenant.getCompanyId(), pageable)
                .map(PayableService::toPayment);
    }

    @Transactional
    public PayableDtos.PaymentResponse createPayment(PayableDtos.PaymentRequest req) {
        Long companyId = tenant.getCompanyId();
        Supplier supplier = suppliers.findByIdAndCompanyId(req.supplierId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + req.supplierId() + " no encontrado"));

        SupplierPayment p = new SupplierPayment();
        p.setCompanyId(companyId);
        p.setSupplier(supplier);
        p.setAmount(req.amount());
        p.setPaymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now());
        p.setMethod(req.method());
        p.setReference(req.reference());
        p.setNotes(req.notes());

        if (req.purchaseInvoiceId() != null) {
            PurchaseInvoice inv = invoices.findByIdAndCompanyId(req.purchaseInvoiceId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Factura " + req.purchaseInvoiceId() + " no encontrada"));
            p.setPurchaseInvoice(inv);
            BigDecimal paid = (inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO).add(req.amount());
            inv.setPaidAmount(paid);
            inv.setStatus(paid.compareTo(inv.getAmount()) >= 0 ? "paid" : "partial");
        }
        SupplierPayment saved = paymentsRepo.save(p);

        // Baja el saldo por pagar del proveedor.
        BigDecimal balance = supplier.getBalance() != null ? supplier.getBalance() : BigDecimal.ZERO;
        supplier.setBalance(balance.subtract(req.amount()));
        return toPayment(saved);
    }

    private static PayableDtos.InvoiceResponse toInvoice(PurchaseInvoice i) {
        return new PayableDtos.InvoiceResponse(i.getId(), i.getDocNumber(),
                i.getSupplier() != null ? i.getSupplier().getId() : null,
                i.getSupplier() != null ? i.getSupplier().getName() : null,
                i.getPurchaseOrder() != null ? i.getPurchaseOrder().getId() : null,
                i.getInvoiceDate(), i.getDueDate(), i.getAmount(), i.getPaidAmount(), i.getStatus());
    }

    private static PayableDtos.PaymentResponse toPayment(SupplierPayment p) {
        return new PayableDtos.PaymentResponse(p.getId(),
                p.getSupplier() != null ? p.getSupplier().getId() : null,
                p.getSupplier() != null ? p.getSupplier().getName() : null,
                p.getPurchaseInvoice() != null ? p.getPurchaseInvoice().getId() : null,
                p.getAmount(), p.getPaymentDate(), p.getMethod(), p.getReference(), p.getNotes());
    }
}
