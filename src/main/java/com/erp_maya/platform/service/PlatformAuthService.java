package com.erp_maya.platform.service;

import com.erp_maya.common.InvalidCredentialsException;
import com.erp_maya.platform.domain.PlatformOperator;
import com.erp_maya.platform.dto.PlatformDtos;
import com.erp_maya.platform.repository.PlatformOperatorRepository;
import com.erp_maya.platform.security.PlatformAccess;
import com.erp_maya.security.service.PasswordEncoder;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.generator.TokenGenerator;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Login de los operadores de la consola. El token lleva {@code kind=platform}
 * y nunca {@code companyId}: el {@code PlatformAudienceFilter} lo deja entrar
 * solo a {@code /api/platform/**}.
 *
 * Tras {@link #MAX_FAILED} contraseñas malas seguidas la cuenta se bloquea
 * {@link #LOCK} — es la consola que administra a todas las empresas.
 */
@Singleton
public class PlatformAuthService {

    static final int MAX_FAILED = 5;
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final String BAD = "Correo o contraseña incorrectos";

    private final PlatformOperatorRepository operators;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final int expiration;
    /** Para comparar contra algo cuando el correo no existe: misma demora, no delata cuentas. */
    private final String hashDeRelleno;

    public PlatformAuthService(PlatformOperatorRepository operators, PasswordEncoder passwordEncoder,
                               TokenGenerator tokenGenerator,
                               @Value("${erp.platform.token-expiration:28800}") int expiration) {
        this.operators = operators;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.expiration = expiration;
        this.hashDeRelleno = passwordEncoder.encode("relleno-" + System.nanoTime());
    }

    public static String normalizeEmail(@Nullable String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static boolean locked(@Nullable Instant lockedUntil, Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Tras un fallo: hasta cuándo queda bloqueada (null = no se bloquea). */
    static @Nullable Instant lockAfterFailure(int failedSoFar, Instant now) {
        return failedSoFar >= MAX_FAILED ? now.plus(LOCK) : null;
    }

    // Sin @Transactional a propósito: el contador de fallos debe guardarse
    // aunque el login termine en excepción (cada update va en su propia transacción).
    public PlatformDtos.LoginResponse login(PlatformDtos.LoginRequest req) {
        Instant now = Instant.now();
        PlatformOperator op = operators.findByEmail(normalizeEmail(req.email())).orElse(null);
        if (op == null) {
            passwordEncoder.matches(req.password(), hashDeRelleno);
            throw new InvalidCredentialsException(BAD);
        }
        if (locked(op.getLockedUntil(), now)) {
            throw new InvalidCredentialsException("Demasiados intentos. Prueba de nuevo en unos minutos.");
        }
        if (!passwordEncoder.matches(req.password(), op.getPasswordHash())) {
            int fallos = op.getFailedAttempts() + 1;
            Instant bloqueo = lockAfterFailure(fallos, now);
            op.setFailedAttempts(bloqueo == null ? fallos : 0);
            op.setLockedUntil(bloqueo);
            operators.update(op);
            throw new InvalidCredentialsException(BAD);
        }
        // La contraseña se valida antes que el estado: a quien no la sabe no se
        // le confirma que la cuenta existe.
        if (!PlatformOperator.ACTIVE.equals(op.getStatus())) {
            throw new InvalidCredentialsException("Tu acceso a la consola está deshabilitado");
        }
        op.setFailedAttempts(0);
        op.setLockedUntil(null);
        op.setLastLoginAt(now);
        operators.update(op);
        return new PlatformDtos.LoginResponse(token(op), info(op));
    }

    /** El operador del token, releído de la base: si lo deshabilitaron, deja de servir. */
    @Transactional
    public PlatformDtos.OperatorInfo me(Authentication auth) {
        PlatformOperator op = operators.findById(PlatformAccess.operatorId(auth))
                .filter(o -> PlatformOperator.ACTIVE.equals(o.getStatus()))
                .orElseThrow(() -> new InvalidCredentialsException("Sesión vencida"));
        return info(op);
    }

    public static PlatformDtos.OperatorInfo info(PlatformOperator op) {
        return new PlatformDtos.OperatorInfo(op.getId(), op.getName(), op.getEmail(), op.getRole());
    }

    private String token(PlatformOperator op) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(PlatformAccess.KIND, PlatformAccess.KIND_PLATFORM);
        attributes.put("operatorId", op.getId());
        attributes.put("email", op.getEmail());
        attributes.put("name", op.getName());
        attributes.put("role", op.getRole());
        // Sujeto con prefijo: nunca se confunde con el id de un usuario de empresa.
        Authentication authentication = Authentication.build("op:" + op.getId(), List.of(op.getRole()), attributes);
        return tokenGenerator.generateToken(authentication, expiration)
                .orElseThrow(() -> new IllegalStateException("No se pudo generar el token de la consola."));
    }
}
