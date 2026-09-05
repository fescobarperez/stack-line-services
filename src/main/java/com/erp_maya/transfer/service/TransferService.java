package com.erp_maya.transfer.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.inventory.service.StockService;
import com.erp_maya.transfer.domain.Transfer;
import com.erp_maya.transfer.domain.TransferItem;
import com.erp_maya.transfer.dto.TransferDtos;
import com.erp_maya.transfer.repository.TransferRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;

@Singleton
public class TransferService {

    private final TransferRepository transfers;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final StockService stockService;
    private final TenantContext tenant;

    public TransferService(TransferRepository transfers, ProductRepository products,
                           BranchRepository branches, StockService stockService, TenantContext tenant) {
        this.transfers = transfers;
        this.products = products;
        this.branches = branches;
        this.stockService = stockService;
        this.tenant = tenant;
    }

    @Transactional
    public Page<TransferDtos.Response> list(Pageable pageable) {
        return transfers.findByCompanyIdOrderByTransferDateDesc(tenant.getCompanyId(), pageable)
                .map(TransferService::toResponse);
    }

    @Transactional
    public TransferDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public TransferDtos.Response create(TransferDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        Branch from = branches.findByIdAndCompanyId(req.fromBranchId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal origen " + req.fromBranchId() + " no encontrada"));
        Branch to = branches.findByIdAndCompanyId(req.toBranchId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal destino " + req.toBranchId() + " no encontrada"));

        Transfer tr = new Transfer();
        tr.setCompanyId(companyId);
        tr.setFromBranch(from);
        tr.setToBranch(to);
        tr.setTransporter(req.transporter());
        tr.setTransferDate(req.transferDate());
        tr.setStatus("draft");
        tr.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : "TR-" + Instant.now().toEpochMilli());
        for (TransferDtos.ItemRequest ir : req.items()) {
            Product product = products.findByIdAndCompanyId(ir.productId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto " + ir.productId() + " no encontrado"));
            TransferItem item = new TransferItem();
            item.setProduct(product);
            item.setQuantity(ir.quantity());
            item.setQtyReceived(java.math.BigDecimal.ZERO);
            tr.addItem(item);
        }
        return toResponse(transfers.save(tr));
    }

    /** Despacha: descuenta stock de la sucursal origen y pasa a in_transit. */
    @Transactional
    public TransferDtos.Response dispatch(Long id) {
        Transfer tr = find(id);
        if (!"draft".equals(tr.getStatus())) {
            throw new IllegalStateException("Solo se puede despachar un traslado en borrador.");
        }
        for (TransferItem item : tr.getItems()) {
            stockService.applyMovement(item.getProduct(), tr.getFromBranch(), null, "transfer",
                    item.getQuantity().negate(), "transfer", tr.getDocNumber(), "");
        }
        tr.setStatus("in_transit");
        return toResponse(transfers.update(tr));
    }

    /** Recibe en destino: ingresa stock a la sucursal destino y pasa a completed. */
    @Transactional
    public TransferDtos.Response receive(Long id) {
        Transfer tr = find(id);
        if (!"in_transit".equals(tr.getStatus())) {
            throw new IllegalStateException("Solo se puede recibir un traslado en tránsito.");
        }
        for (TransferItem item : tr.getItems()) {
            stockService.applyMovement(item.getProduct(), tr.getToBranch(), null, "transfer",
                    item.getQuantity(), "transfer", tr.getDocNumber(), "");
            item.setQtyReceived(item.getQuantity());
        }
        tr.setStatus("completed");
        return toResponse(transfers.update(tr));
    }

    private Transfer find(Long id) {
        return transfers.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Traslado " + id + " no encontrado"));
    }

    private static TransferDtos.Response toResponse(Transfer tr) {
        var items = tr.getItems().stream().map(i -> new TransferDtos.ItemResponse(
                i.getId(), i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProduct() != null ? i.getProduct().getName() : null,
                i.getQuantity(), i.getQtyReceived())).toList();
        return new TransferDtos.Response(tr.getId(), tr.getDocNumber(),
                tr.getFromBranch() != null ? tr.getFromBranch().getId() : null,
                tr.getFromBranch() != null ? tr.getFromBranch().getName() : null,
                tr.getToBranch() != null ? tr.getToBranch().getId() : null,
                tr.getToBranch() != null ? tr.getToBranch().getName() : null,
                tr.getTransporter(), tr.getTransferDate(), tr.getStatus(), items);
    }
}
