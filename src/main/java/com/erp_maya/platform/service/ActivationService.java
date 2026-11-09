package com.erp_maya.platform.service;

import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.platform.domain.UserInvitation;
import com.erp_maya.platform.repository.UserInvitationRepository;
import com.erp_maya.platform.security.PlatformException;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import com.erp_maya.security.service.PasswordEncoder;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * El usuario invitado abre el enlace de la consola en la app, ve a qué empresa
 * entra y define su contraseña. El enlace sirve una sola vez.
 */
@Singleton
public class ActivationService {

    static final int MIN_PASSWORD = 8;

    private final UserInvitationRepository invitations;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder passwordEncoder;

    public ActivationService(UserInvitationRepository invitations, UserRepository users,
                             CompanyRepository companies, PasswordEncoder passwordEncoder) {
        this.invitations = invitations;
        this.users = users;
        this.companies = companies;
        this.passwordEncoder = passwordEncoder;
    }

    @Serdeable
    public record ActivationInfo(String name, String email, String companyName, String companyCode) {}

    @Serdeable
    public record ActivateRequest(@Nullable String token, @Nullable String password) {}

    /** Contraseña aceptable: 8+ caracteres, con letras y números. */
    public static boolean passwordOk(@Nullable String p) {
        return p != null && p.length() >= MIN_PASSWORD && p.chars().anyMatch(Character::isLetter)
                && p.chars().anyMatch(Character::isDigit);
    }

    @Transactional
    public ActivationInfo info(@Nullable String token) {
        Valida v = validar(token);
        return new ActivationInfo(v.user().getName(), v.user().getEmail(), v.company().getName(), v.company().getCode());
    }

    @Transactional
    public ActivationInfo activate(ActivateRequest req) {
        Valida v = validar(req.token());
        if (!passwordOk(req.password())) {
            throw new PlatformException(HttpStatus.BAD_REQUEST, "VALIDATION", "Revisa los campos marcados",
                    Map.of("password", "Mínimo 8 caracteres, con letras y números"));
        }
        User u = v.user();
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setStatus("active");
        users.update(u);
        v.invitation().setUsedAt(Instant.now());
        invitations.update(v.invitation());
        return new ActivationInfo(u.getName(), u.getEmail(), v.company().getName(), v.company().getCode());
    }

    private record Valida(UserInvitation invitation, User user, Company company) {}

    private Valida validar(@Nullable String token) {
        UserInvitation inv = token == null || token.isBlank() ? null
                : invitations.findByTokenHash(InvitationService.hash(token)).orElse(null);
        // Un solo mensaje para «no existe», «vencido» o «ya usado»: no se distingue a quien prueba tokens.
        if (inv == null || !inv.usable(Instant.now())) {
            throw new PlatformException(HttpStatus.GONE, "INVITATION_INVALID",
                    "El enlace ya no es válido. Pide uno nuevo al administrador de Stackline.", null);
        }
        User u = users.findByIdAndCompanyId(inv.getUserId(), inv.getCompanyId())
                .filter(x -> "invited".equals(x.getStatus()))
                .orElseThrow(() -> new PlatformException(HttpStatus.GONE, "INVITATION_INVALID",
                        "El enlace ya no es válido. Pide uno nuevo al administrador de Stackline.", null));
        Company c = companies.findById(inv.getCompanyId()).orElseThrow();
        if (!CompanyRules.operates(c.getStatus())) {
            throw new PlatformException(HttpStatus.GONE, "COMPANY_DISABLED", "La empresa está suspendida. Contacta al administrador.", null);
        }
        return new Valida(inv, u, c);
    }
}
