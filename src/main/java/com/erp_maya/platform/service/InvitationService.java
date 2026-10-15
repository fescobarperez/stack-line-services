package com.erp_maya.platform.service;

import com.erp_maya.platform.domain.UserInvitation;
import com.erp_maya.platform.repository.UserInvitationRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Enlaces de activación de cuenta. El token (32 bytes al azar) solo viaja en
 * la URL que se muestra una vez en la consola; la base guarda su SHA-256. Uno
 * nuevo revoca los anteriores del mismo usuario.
 */
@Singleton
public class InvitationService {

    static final Duration VIGENCIA = Duration.ofHours(72);
    static final String DEV_URL = "http://localhost:5173/activar";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserInvitationRepository invitations;
    private final String activationUrl;

    public InvitationService(UserInvitationRepository invitations,
                             @Value("${erp.platform.activation-url:}") String activationUrl) {
        this.invitations = invitations;
        // Variable vacía en el compose = no configurada: se usa la de desarrollo y se avisa.
        if (activationUrl == null || activationUrl.isBlank()) {
            LoggerFactory.getLogger(InvitationService.class)
                    .warn("PLATFORM_ACTIVATION_URL no está definida: los enlaces de invitación apuntan a {}", DEV_URL);
            this.activationUrl = DEV_URL;
        } else {
            this.activationUrl = activationUrl.trim();
        }
    }

    @Serdeable
    public record Invitation(String email, String url, Instant expiresAt) {}

    public static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Crea un enlace nuevo para el usuario y revoca los anteriores. Va dentro de la transacción de quien llama. */
    public Invitation issue(Long userId, Long companyId, String email, String createdBy) {
        Instant now = Instant.now();
        for (UserInvitation prev : invitations.findByUserId(userId)) {
            if (prev.getUsedAt() == null && prev.getRevokedAt() == null) {
                prev.setRevokedAt(now);
                invitations.update(prev);
            }
        }
        String token = newToken();
        UserInvitation inv = new UserInvitation();
        inv.setUserId(userId);
        inv.setCompanyId(companyId);
        inv.setTokenHash(hash(token));
        inv.setExpiresAt(now.plus(VIGENCIA));
        inv.setCreatedBy(createdBy);
        invitations.save(inv);
        String sep = activationUrl.contains("?") ? "&" : "?";
        return new Invitation(email, activationUrl + sep + "t=" + token, inv.getExpiresAt());
    }
}
