package com.erp_maya.security.service;

import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.security.domain.Role;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.RoleRepository;
import com.erp_maya.security.repository.UserRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Siembra datos mínimos para probar el login end-to-end en desarrollo: una empresa
 * con código, un rol admin y un usuario con password conocido. Solo se activa con
 * {@code erp.seed.enabled=true} y no hace nada si la empresa ya existe (idempotente).
 */
@Singleton
@Requires(property = "erp.seed.enabled", value = "true")
public class DevDataSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String COMPANY_CODE = "TIENDA-DEMO";
    private static final String ADMIN_EMAIL = "admin@demo.gt";
    private static final String ADMIN_PASSWORD = "admin123";

    private final CompanyRepository companies;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(CompanyRepository companies, RoleRepository roles,
                         UserRepository users, PasswordEncoder passwordEncoder) {
        this.companies = companies;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener
    @Transactional
    public void onStartup(StartupEvent event) {
        if (companies.findByCode(COMPANY_CODE).isPresent()) {
            return;
        }

        Company company = new Company();
        company.setCode(COMPANY_CODE);
        company.setName("Tienda Demo ERP MAYA");
        company.setNit("1234567-8");
        company.setPlan("standard");
        company.setStatus("active");
        company = companies.save(company);

        Role admin = new Role();
        admin.setCompanyId(company.getId());
        admin.setName("Administrador");
        admin.setDescription("Acceso total (seed de desarrollo)");
        admin.setPermissions(List.of("*"));
        admin = roles.save(admin);

        User user = new User();
        user.setCompanyId(company.getId());
        user.setRole(admin);
        user.setName("Administrador Demo");
        user.setEmail(ADMIN_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        user.setStatus("active");
        users.save(user);

        LOG.info("Seed de desarrollo creado: empresa '{}' / login {} · {} / password '{}'",
                COMPANY_CODE, COMPANY_CODE, ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}
