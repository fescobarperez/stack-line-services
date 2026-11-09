package com.erp_maya.platform.service;

import com.erp_maya.platform.domain.PlatformOperator;
import com.erp_maya.platform.repository.PlatformOperatorRepository;
import com.erp_maya.security.service.PasswordEncoder;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primer dueño de la consola. Solo actúa si no hay ningún operador y están
 * definidos {@code PLATFORM_OWNER_EMAIL} y {@code PLATFORM_OWNER_PASSWORD}: sin
 * contraseña por defecto en un servidor. Los demás operadores se crean desde
 * la consola (fase siguiente).
 */
@Singleton
public class PlatformOwnerSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformOwnerSeeder.class);
    static final int MIN_PASSWORD = 12;

    private final PlatformOperatorRepository operators;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String name;
    private final String password;
    private final boolean dev;

    public PlatformOwnerSeeder(PlatformOperatorRepository operators, PasswordEncoder passwordEncoder,
                               Environment environment,
                               @Value("${erp.platform.owner.email:}") String email,
                               @Value("${erp.platform.owner.name:Dueño}") String name,
                               @Value("${erp.platform.owner.password:}") String password) {
        this.operators = operators;
        this.passwordEncoder = passwordEncoder;
        this.email = PlatformAuthService.normalizeEmail(email);
        this.name = name == null || name.isBlank() ? "Dueño" : name.trim();
        this.password = password;
        this.dev = environment.getActiveNames().contains("dev");
    }

    static boolean passwordAcceptable(String password, boolean dev) {
        return password != null && (dev ? !password.isBlank() : password.length() >= MIN_PASSWORD);
    }

    @EventListener
    @Transactional
    public void onStartup(StartupEvent event) {
        if (operators.count() > 0) {
            return;
        }
        if (email.isBlank() || !passwordAcceptable(password, dev)) {
            LOG.warn("No hay operadores de la consola. Define PLATFORM_OWNER_EMAIL y PLATFORM_OWNER_PASSWORD "
                     + "(mínimo {} caracteres) para crear el primero.", MIN_PASSWORD);
            return;
        }
        PlatformOperator op = new PlatformOperator();
        op.setEmail(email);
        op.setName(name);
        op.setRole(PlatformOperator.OWNER);
        op.setPasswordHash(passwordEncoder.encode(password));
        operators.save(op);
        LOG.info("Primer dueño de la consola creado: {}", email);
    }
}
