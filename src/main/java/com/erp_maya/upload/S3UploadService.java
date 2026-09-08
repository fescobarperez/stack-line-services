package com.erp_maya.upload;

import com.erp_maya.common.TenantContext;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Emite URLs prefirmadas para que el navegador suba logos directo a S3 sin que
 * el archivo pase por el backend ni las credenciales lleguen al cliente.
 *
 * Las credenciales las resuelve la cadena por defecto del SDK: en producción,
 * el IAM Role de la instancia (EC2/ECS/Lambda); en local, tu perfil AWS.
 */
@Singleton
public class S3UploadService {

    // Solo imágenes; mapea content-type -> extensión de archivo.
    private static final Map<String, String> ALLOWED = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg"
    );

    private final S3UploadProperties props;
    private final TenantContext tenant;
    private final S3Presigner presigner;

    public S3UploadService(S3UploadProperties props, TenantContext tenant) {
        this.props = props;
        this.tenant = tenant;
        this.presigner = S3Presigner.builder()
                .region(Region.of(props.getRegion()))
                .build();
    }

    public UploadDtos.LogoUploadResponse presignLogoUpload(UploadDtos.LogoUploadRequest req) {
        String contentType = req.contentType().trim().toLowerCase();
        String ext = ALLOWED.get(contentType);
        if (ext == null) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Formato no permitido. Usa PNG, JPG, WEBP o SVG.");
        }

        Long companyId = tenant.getCompanyId();
        // Key única por empresa: logos/company-<id>/<uuid>.<ext>
        String prefix = props.getLogoPrefix();
        if (!prefix.endsWith("/")) prefix = prefix + "/";
        String key = "%scompany-%d/%s.%s".formatted(prefix, companyId, UUID.randomUUID(), ext);

        // Firmamos content-type para que el objeto quede guardado con su tipo
        // (image/png…) y el navegador lo muestre inline en el PDF. El frontend
        // DEBE mandar exactamente este mismo Content-Type en el PUT.
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignExpirySeconds()))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        String publicUrl = "https://%s.s3.%s.amazonaws.com/%s".formatted(
                props.getBucket(), props.getRegion(), key);

        return new UploadDtos.LogoUploadResponse(
                presigned.url().toString(),
                publicUrl,
                contentType,
                props.getPresignExpirySeconds());
    }

    @PreDestroy
    void close() {
        presigner.close();
    }

    // Referenciado para dejar claro qué extensiones se aceptan (uso en validaciones futuras).
    Set<String> allowedContentTypes() {
        return ALLOWED.keySet();
    }
}
