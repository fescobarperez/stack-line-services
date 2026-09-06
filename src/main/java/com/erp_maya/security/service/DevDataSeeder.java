package com.erp_maya.security.service;

import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.security.domain.Role;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.RoleRepository;
import com.erp_maya.security.repository.UserRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
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

    private final CompanyRepository companies;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /**
     * Datos del primer acceso. Los valores por defecto son los de desarrollo de
     * siempre; en un servidor se sobreescriben por variable de entorno.
     *
     * La contraseña se parametriza para poder usar esto como arranque de una
     * instalación nueva: apagar el seeder deja el sistema sin forma de entrar,
     * y dejar 'admin123' en una IP pública es una puerta abierta.
     */
    private final String companyCode;
    private final String companyName;
    private final String companyNit;
    private final String adminEmail;
    private final String adminPassword;

    public DevDataSeeder(CompanyRepository companies, RoleRepository roles,
                         UserRepository users, PasswordEncoder passwordEncoder,
                         @Value("${erp.seed.company-code:TIENDA-DEMO}") String companyCode,
                         @Value("${erp.seed.company-name:Tienda Demo ERP MAYA}") String companyName,
                         @Value("${erp.seed.company-nit:1234567-8}") String companyNit,
                         @Value("${erp.seed.admin-email:admin@demo.gt}") String adminEmail,
                         @Value("${erp.seed.admin-password:admin123}") String adminPassword) {
        this.companyCode = companyCode;
        this.companyName = companyName;
        this.companyNit = companyNit;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.companies = companies;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener
    @Transactional
    public void onStartup(StartupEvent event) {
        if (companies.findByCode(companyCode).isPresent()) {
            return;
        }
        // El código es distinto pero el NIT ya está tomado: sembrar aquí
        // rompería con una violación de unicidad a mitad del arranque, y el
        // servicio no levanta. Pasa al apuntar una instalación nueva contra
        // una base que ya tiene empresa.
        if (companies.findByNit(companyNit).isPresent()) {
            LOG.warn("No se sembró '{}': el NIT {} ya pertenece a otra empresa. "
                     + "Define erp.seed.company-nit o apaga el seed.", companyCode, companyNit);
            return;
        }

        Company company = new Company();
        company.setCode(companyCode);
        company.setName(companyName);
        company.setNit(companyNit);
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
        user.setEmail(adminEmail);
        user.setPasswordHash(passwordEncoder.encode(adminPassword));
        user.setStatus("active");
        users.save(user);

        // La contraseña no se registra: en desarrollo se sabe cuál es, y en un
        // servidor no tiene por qué quedar escrita en el log.
        LOG.info("Instalación inicial creada: empresa '{}' · usuario {}", companyCode, adminEmail);
    }
}
