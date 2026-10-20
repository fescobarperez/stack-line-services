package com.erp_maya.catalog.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.domain.ProductImage;
import com.erp_maya.catalog.dto.ProductImageDtos;
import com.erp_maya.catalog.repository.ProductImageRepository;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * La foto del producto. Una por producto; subir otra la reemplaza. La versión
 * (products.image_updated_at) cambia en cada subida para que el navegador no
 * muestre la anterior desde su caché.
 */
@Singleton
public class ProductImageService {

    /** El navegador la manda a ~1000 px; esto alcanza con mucho margen. */
    static final int MAX_FULL = 3 * 1024 * 1024;
    static final int MAX_THUMB = 512 * 1024;
    static final Set<String> MIMES = Set.of("image/png", "image/jpeg", "image/webp");

    public static final String THUMB = "thumb";

    private final ProductImageRepository images;
    private final ProductRepository products;
    private final TenantContext tenant;

    public ProductImageService(ProductImageRepository images, ProductRepository products, TenantContext tenant) {
        this.images = images;
        this.products = products;
        this.tenant = tenant;
    }

    @Transactional
    public ProductImageDtos.Saved save(Long productId, ProductImageDtos.Upload req, String actor) {
        Long companyId = tenant.getCompanyId();
        Product p = producto(productId, companyId);
        byte[] full = decodificar(req.imageBase64(), req.imageMime(), MAX_FULL, "La imagen");
        byte[] thumb = decodificar(req.thumbBase64(), req.thumbMime(), MAX_THUMB, "La miniatura");
        // Al milisegundo: es lo que viaja como versión y lo que se compara en el ETag.
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Optional<ProductImage> previa = images.findByProductIdAndCompanyId(productId, companyId);
        ProductImage img = previa.orElseGet(ProductImage::new);
        img.setProductId(productId);
        img.setCompanyId(companyId);
        img.setImageData(full);
        img.setImageMime(mime(req.imageMime()));
        img.setThumbData(thumb);
        img.setThumbMime(mime(req.thumbMime()));
        img.setUpdatedBy(actor);
        img.setUpdatedAt(ahora);
        if (previa.isPresent()) {
            images.update(img);
        } else {
            images.save(img);
        }
        p.setImageUpdatedAt(ahora);
        products.update(p);
        return new ProductImageDtos.Saved(productId, ahora.toEpochMilli());
    }

    @Transactional
    public void delete(Long productId) {
        Long companyId = tenant.getCompanyId();
        Product p = producto(productId, companyId);
        images.findByProductIdAndCompanyId(productId, companyId).ifPresent(images::delete);
        if (p.getImageUpdatedAt() != null) {
            p.setImageUpdatedAt(null);
            products.update(p);
        }
    }

    /** La foto (o su miniatura) con su versión, si el producto tiene. */
    @Transactional
    public Optional<Imagen> image(Long productId, String size) {
        boolean mini = THUMB.equalsIgnoreCase(size);
        return images.findByProductIdAndCompanyId(productId, tenant.getCompanyId())
                .map(i -> new Imagen(mini ? i.getThumbData() : i.getImageData(),
                        mini ? i.getThumbMime() : i.getImageMime(),
                        i.getUpdatedAt().toEpochMilli()));
    }

    public record Imagen(byte[] bytes, String mime, long version) {
    }

    private Product producto(Long id, Long companyId) {
        return products.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + id + " no encontrado"));
    }

    private static String mime(String m) {
        return m.trim().toLowerCase();
    }

    /** Base64 → bytes, validando tipo y tamaño. */
    static byte[] decodificar(String base64, String mime, int max, String que) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(que + " viene vacía");
        }
        String tipo = mime == null ? "" : mime.trim().toLowerCase();
        if (!MIMES.contains(tipo)) {
            throw new IllegalStateException("Tipo de imagen no permitido: " + mime + " (usa JPG, PNG o WebP)");
        }
        // 4 caracteres base64 = 3 bytes: se rechaza antes de decodificar.
        if ((long) base64.length() * 3 / 4 > max) {
            throw new IllegalStateException(que + " pasa de " + (max / 1024) + " KB");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(que + " no es base64 válido");
        }
        if (!coincide(bytes, tipo)) {
            throw new IllegalStateException(que + " no es un " + tipo);
        }
        return bytes;
    }

    /** Revisa la firma del archivo: que el contenido sea lo que dice el tipo. */
    static boolean coincide(byte[] b, String tipo) {
        return switch (tipo) {
            case "image/png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/webp" -> b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}
