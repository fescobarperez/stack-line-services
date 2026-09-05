package com.erp_maya.returns.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.inventory.service.StockService;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.pos.domain.Sale;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.returns.domain.CreditNote;
import com.erp_maya.returns.domain.CreditNoteItem;
import com.erp_maya.returns.dto.CreditNoteDtos;
import com.erp_maya.returns.repository.CreditNoteRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Devoluciones. Al emitir la nota de crédito reingresa el stock devuelto. */
@Singleton
public class CreditNoteService {

    private final CreditNoteRepository notes;
    private final ProductRepository products;
    private final SaleRepository sales;
    private final ClientRepository clients;
    private final StockService stockService;
    private final TenantContext tenant;

    public CreditNoteService(CreditNoteRepository notes, ProductRepository products, SaleRepository sales,
                             ClientRepository clients, StockService stockService, TenantContext tenant) {
        this.notes = notes;
        this.products = products;
        this.sales = sales;
        this.clients = clients;
        this.stockService = stockService;
        this.tenant = tenant;
    }

    @Transactional
    public Page<CreditNoteDtos.Response> list(Pageable pageable) {
        return notes.findByCompanyIdOrderByReturnDateDesc(tenant.getCompanyId(), pageable)
                .map(CreditNoteService::toResponse);
    }

    @Transactional
    public CreditNoteDtos.Response get(Long id) {
        return toResponse(notes.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Nota de crédito " + id + " no encontrada")));
    }

    @Transactional
    public CreditNoteDtos.Response create(CreditNoteDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        CreditNote note = new CreditNote();
        note.setCompanyId(companyId);
        note.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : "NC-" + Instant.now().toEpochMilli());
        note.setReturnDate(req.returnDate() != null ? req.returnDate() : LocalDate.now());
        note.setReason(req.reason());
        note.setRefundMethod(req.refundMethod());
        note.setStatus("issued");
        note.setNoteType(req.noteType() != null && !req.noteType().isBlank() ? req.noteType() : "devolucion");
        note.setTicketRef(req.ticketRef());
        note.setClientName(req.clientName());
        note.setClientNit(req.clientNit());
        note.setCashier(req.cashier());
        note.setBranchName(req.branchName());
        // FEL simulado: se autoriza al emitir (no hay integración real con SAT).
        note.setFelStatus("autorizada");
        note.setFelUuid(UUID.randomUUID().toString());

        Sale sale = null;
        if (req.saleId() != null) {
            sale = sales.findByIdAndCompanyId(req.saleId(), companyId).orElse(null);
            note.setSale(sale);
        }
        if (req.clientId() != null) {
            clients.findByIdAndCompanyId(req.clientId(), companyId).ifPresent(note::setClient);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CreditNoteDtos.ItemRequest ir : req.items()) {
            BigDecimal unitPrice = ir.unitPrice() != null ? ir.unitPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(ir.quantity());
            CreditNoteItem item = new CreditNoteItem();
            if (ir.productId() != null) {
                Product product = products.findByIdAndCompanyId(ir.productId(), companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Producto " + ir.productId() + " no encontrado"));
                item.setProduct(product);
            }
            item.setItemName(ir.itemName());
            item.setQuantity(ir.quantity());
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
            note.addItem(item);
            total = total.add(lineTotal);
        }
        note.setTotal(total);
        CreditNote saved = notes.save(note);

        // Reingresa el stock a la sucursal de la venta original (si hay).
        if (sale != null && sale.getBranch() != null) {
            for (CreditNoteItem item : saved.getItems()) {
                stockService.applyMovement(item.getProduct(), sale.getBranch(), null, "return",
                        item.getQuantity(), "credit_note", saved.getDocNumber(), "");
            }
        }
        return toResponse(saved);
    }

    @Transactional
    public CreditNoteDtos.Response retryFel(Long id, CreditNoteDtos.FelRequest req) {
        CreditNote note = notes.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Nota de crédito " + id + " no encontrada"));
        String status = req != null && req.felStatus() != null && !req.felStatus().isBlank()
                ? req.felStatus() : "autorizada";
        note.setFelStatus(status);
        if ("autorizada".equals(status)) {
            note.setFelUuid(req != null && req.felUuid() != null ? req.felUuid() : UUID.randomUUID().toString());
            note.setFelError(null);
        } else if (req != null) {
            note.setFelError(req.felError());
        }
        return toResponse(notes.update(note));
    }

    private static CreditNoteDtos.Response toResponse(CreditNote n) {
        var items = n.getItems().stream().map(i -> new CreditNoteDtos.ItemResponse(
                i.getId(), i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProduct() != null ? i.getProduct().getName() : i.getItemName(),
                i.getQuantity(), i.getUnitPrice(), i.getLineTotal())).toList();
        return new CreditNoteDtos.Response(n.getId(), n.getDocNumber(), n.getNoteType(), n.getTicketRef(),
                n.getSale() != null ? n.getSale().getId() : null,
                n.getClient() != null ? n.getClient().getId() : null,
                n.getClientName(), n.getClientNit(), n.getCashier(), n.getBranchName(),
                n.getReturnDate(), n.getReason(), n.getRefundMethod(), n.getTotal(), n.getStatus(),
                n.getFelStatus(), n.getFelUuid(), n.getFelError(), items);
    }
}
