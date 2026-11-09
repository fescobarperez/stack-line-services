package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.quote.domain.QuoteDesign;
import com.erp_maya.quote.dto.QuoteDesignDtos;
import com.erp_maya.quote.repository.QuoteDesignRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * El diseño que el cliente aprobó con el asistente. Una por cotización: si
 * vuelve a aprobar otro render, se reemplaza.
 */
@Singleton
public class QuoteDesignService {

    /** Un render de WhatsApp ronda 1–2 MB; esto deja margen sin abrir la puerta a cualquier archivo. */
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final Set<String> MIMES = Set.of("image/png", "image/jpeg", "image/webp");

    private final QuoteDesignRepository designs;
    private final QuoteRepository quotes;
    private final TenantContext tenant;
    private final ObjectMapper json;

    public QuoteDesignService(QuoteDesignRepository designs, QuoteRepository quotes, TenantContext tenant, ObjectMapper json) {
        this.designs = designs;
        this.quotes = quotes;
        this.tenant = tenant;
        this.json = json;
    }

    @Transactional
    public QuoteDesignDtos.Response save(Long quoteId, QuoteDesignDtos.Save req, String actor) {
        Long companyId = tenant.getCompanyId();
        quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        byte[] imagen = decodificar(req.imageBase64(), req.imageMime());

        QuoteDesign d = designs.findByQuoteIdAndCompanyId(quoteId, companyId).orElseGet(QuoteDesign::new);
        boolean nueva = d.getId() == null;
        d.setCompanyId(companyId);
        d.setQuoteId(quoteId);
        d.setSpecJson(escribir(req.spec()));
        d.setRenderNumber(req.renderNumber());
        d.setRenderMax(req.renderMax());
        // Sin imagen nueva se conserva la anterior (p. ej. solo cambió la ficha).
        if (imagen != null) {
            d.setImageData(imagen);
            d.setImageMime(req.imageMime().trim().toLowerCase());
        }
        d.setCreatedBy(actor);
        QuoteDesign guardado = nueva ? designs.save(d) : designs.update(d);
        return respuesta(guardado);
    }

    @Transactional
    public Optional<QuoteDesignDtos.Response> get(Long quoteId) {
        return designs.findByQuoteIdAndCompanyId(quoteId, tenant.getCompanyId()).map(this::respuesta);
    }

    /** La imagen y su tipo, si hay. */
    @Transactional
    public Optional<Imagen> image(Long quoteId) {
        return designs.findByQuoteIdAndCompanyId(quoteId, tenant.getCompanyId())
                .filter(d -> d.getImageData() != null && d.getImageData().length > 0)
                .map(d -> new Imagen(d.getImageData(), d.getImageMime()));
    }

    public record Imagen(byte[] bytes, String mime) {
    }

    /** Base64 → bytes, validando tipo y tamaño. Nulo si no vino imagen. */
    static byte[] decodificar(String base64, String mime) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String tipo = mime == null ? "" : mime.trim().toLowerCase();
        if (!MIMES.contains(tipo)) {
            throw new IllegalStateException("Tipo de imagen no permitido: " + mime);
        }
        // Tamaño aproximado antes de decodificar: 4 caracteres base64 = 3 bytes.
        if ((long) base64.length() * 3 / 4 > MAX_BYTES) {
            throw new IllegalStateException("La imagen pasa de " + (MAX_BYTES / 1_048_576) + " MB");
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La imagen no es base64 válido");
        }
    }

    private QuoteDesignDtos.Response respuesta(QuoteDesign d) {
        return new QuoteDesignDtos.Response(
                d.getQuoteId(), leer(d.getSpecJson()), d.getRenderNumber(), d.getRenderMax(),
                d.getImageData() != null && d.getImageData().length > 0, d.getCreatedBy(), d.getUpdatedAt());
    }

    private String escribir(QuoteDesignDtos.Spec spec) {
        try {
            return json.writeValueAsString(spec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private QuoteDesignDtos.Spec leer(String texto) {
        try {
            return json.readValue(texto, QuoteDesignDtos.Spec.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
