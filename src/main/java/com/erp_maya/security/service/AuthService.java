package com.erp_maya.security.service;

import com.erp_maya.common.InvalidCredentialsException;
import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.security.domain.Role;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.dto.AuthDtos;
import com.erp_maya.security.repository.UserRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.generator.TokenGenerator;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Autenticación real: valida código de empresa + email + password y emite un
 * JWT cuyo claim {@code companyId} fija el tenant en cada request posterior.
 */
@Singleton
public class AuthService {

    private final CompanyRepository companies;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final int tokenExpiration;

    public AuthService(CompanyRepository companies,
                       UserRepository users,
                       PasswordEncoder passwordEncoder,
                       TokenGenerator tokenGenerator,
                       @Value("${erp.security.token-expiration:28800}") int tokenExpiration) {
        this.companies = companies;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.tokenExpiration = tokenExpiration;
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest req) {
        Company company = companies.findByCode(req.companyCode().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Empresa, usuario o contraseña inválidos."));
        if (!"active".equals(company.getStatus())) {
            throw new InvalidCredentialsException("La empresa está suspendida. Contacta al administrador.");
        }

        User user = users.findByCompanyIdAndEmail(company.getId(), req.email().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Empresa, usuario o contraseña inválidos."));
        if (!"active".equals(user.getStatus())) {
            throw new InvalidCredentialsException("El usuario está inactivo. Contacta al administrador.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Empresa, usuario o contraseña inválidos.");
        }

        Role role = user.getRole();
        List<String> permissions = role != null && role.getPermissions() != null
                ? role.getPermissions() : List.of();

        user.setLastSeenAt(Instant.now());
        users.update(user);

        String token = generateToken(user, company, role, permissions);
        AuthDtos.UserInfo info = new AuthDtos.UserInfo(
                user.getId(), user.getName(), user.getEmail(),
                company.getId(), company.getName(),
                role != null ? role.getId() : null,
                role != null ? role.getName() : null,
                permissions);
        return new AuthDtos.LoginResponse(token, info);
    }

    private String generateToken(User user, Company company, Role role, List<String> permissions) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("companyId", company.getId());
        attributes.put("companyName", company.getName());
        attributes.put("email", user.getEmail());
        attributes.put("name", user.getName());
        attributes.put("roleId", role != null ? role.getId() : null);
        attributes.put("roleName", role != null ? role.getName() : null);
        attributes.put("permissions", permissions);

        // El "name" de la Authentication es el subject (sub) del JWT: el id del usuario.
        Authentication authentication = Authentication.build(
                String.valueOf(user.getId()), permissions, attributes);

        return tokenGenerator.generateToken(authentication, tokenExpiration)
                .orElseThrow(() -> new IllegalStateException("No se pudo generar el token de sesión."));
    }
}
