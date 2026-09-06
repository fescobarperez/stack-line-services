package com.erp_maya.sequence.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.sequence.domain.DocumentSequence;
import com.erp_maya.sequence.repository.DocumentSequenceRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

/**
 * Numeración correlativa de documentos.
 *
 * Antes de esto no había ninguna: SaleService ponía "T-" + epochMillis, que ni
 * es correlativo ni es legible ni sirve para buscar. Sirve para recibos,
 * facturas y las notas de crédito y débito cuando se expongan.
 */
@Singleton
public class DocumentSequenceService {

    private final DocumentSequenceRepository sequences;
    private final TenantContext tenant;

    public DocumentSequenceService(DocumentSequenceRepository sequences, TenantContext tenant) {
        this.sequences = sequences;
        this.tenant = tenant;
    }

    /**
     * Entrega el siguiente número y lo consume. Debe correr dentro de la misma
     * transacción que el documento: si el documento falla, el correlativo se
     * devuelve con el rollback y no queda un hueco en la numeración.
     *
     * Si no hay serie configurada se crea al vuelo. Es preferible a reventar:
     * un tipo de documento nuevo no debería exigir tocar la base antes de
     * poder emitirlo.
     */
    @Transactional
    public String next(String docType, String series) {
        Long companyId = tenant.getCompanyId();
        String s = series != null && !series.isBlank() ? series : "A";

        DocumentSequence seq = sequences.lockFor(companyId, docType, s).orElseGet(() -> {
            DocumentSequence fresh = new DocumentSequence();
            fresh.setCompanyId(companyId);
            fresh.setDocType(docType);
            fresh.setSeries(s);
            fresh.setPrefix(docType.substring(0, Math.min(3, docType.length())).toUpperCase() + "-");
            fresh.setNextNumber(1L);
            return sequences.save(fresh);
        });

        long number = seq.getNextNumber();
        seq.setNextNumber(number + 1);
        sequences.update(seq);

        int pad = seq.getPadding() != null ? seq.getPadding() : 6;
        return seq.getPrefix() + String.format("%0" + pad + "d", number);
    }
}
