package com.erp_maya.security.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.company.domain.Company;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.dto.ServiceTokenDtos;
import com.erp_maya.security.repository.UserRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.generator.TokenGenerator;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emite el token con el que agents-services opera el ERP en nombre de una empresa.
 *
 * <p>Reemplaza al usuario bot global: cada empresa tiene su propio usuario de
 * sistema «Asistente» (estado {@code system}, sin contraseña, no puede iniciar
 * sesión ni aparece en la lista de usuarios). El token lleva el {@code companyId}
 * de esa empresa, así el {@code TenantFilter} aísla los datos igual que con un
 * usuario normal, y {@code kind=service} para distinguirlo en la bitácora.
 */
@Singleton
public class ServiceTokenService {

    public static final String STATUS_SYSTEM = "system";
    static final String NOMBRE_ASISTENTE = "Asistente";
    static final Set<String> ESTADOS_HABILITADOS = Set.of("active", "trial");

    private final CompanyRepository companies;
    private final UserRepository users;
    private final TokenGenerator tokenGenerator;
    private final String serviceKey;
    private final int expiration;

    public ServiceTokenService(CompanyRepository companies,
                               UserRepository users,
                               TokenGenerator tokenGenerator,
                               @Value("${erp.agent.api-key:}") String serviceKey,
                               @Value("${erp.security.service-token-expiration:3600}") int expiration) {
        this.companies = companies;
        this.users = users;
        this.tokenGenerator = tokenGenerator;
        this.serviceKey = serviceKey;
        this.expiration = expiration;
    }

    /** La llave de servicio recibida coincide con la configurada (comparación en tiempo constante). */
    public boolean llaveValida(String recibida) {
        return llaveValida(serviceKey, recibida);
    }

    static boolean llaveValida(String esperada, String recibida) {
        if (esperada == null || esperada.isBlank() || recibida == null) {
            return false;
        }
        return MessageDigest.isEqual(esperada.getBytes(StandardCharsets.UTF_8),
                recibida.getBytes(StandardCharsets.UTF_8));
    }

    static boolean empresaHabilitada(String status) {
        return status != null && ESTADOS_HABILITADOS.contains(status);
    }

    @Transactional
    public ServiceTokenDtos.Response emitir(Long companyId) {
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa " + companyId + " no encontrada"));
        if (!empresaHabilitada(company.getStatus())) {
            throw new EmpresaNoHabilitadaException(companyId, company.getStatus());
        }
        User asistente = users.findFirstByCompanyIdAndStatus(companyId, STATUS_SYSTEM)
                .orElseGet(() -> crearAsistente(companyId));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", asistente.getId());
        attributes.put("companyId", company.getId());
        attributes.put("companyName", company.getName());
        attributes.put("name", asistente.getName());
        attributes.put("kind", "service");
        attributes.put("permissions", List.of());

        Authentication authentication = Authentication.build(
                String.valueOf(asistente.getId()), List.of(), attributes);
        String token = tokenGenerator.generateToken(authentication, expiration)
                .orElseThrow(() -> new IllegalStateException("No se pudo generar el token de servicio."));
        return new ServiceTokenDtos.Response(token, expiration);
    }

    private User crearAsistente(Long companyId) {
        User u = new User();
        u.setCompanyId(companyId);
        u.setName(NOMBRE_ASISTENTE);
        u.setStatus(STATUS_SYSTEM);
        return users.save(u);
    }

    /** La empresa existe pero está suspendida o cancelada: el asistente no opera. */
    public static class EmpresaNoHabilitadaException extends RuntimeException {
        public EmpresaNoHabilitadaException(Long companyId, String status) {
            super("La empresa " + companyId + " no está habilitada (" + status + ").");
        }
    }
}
