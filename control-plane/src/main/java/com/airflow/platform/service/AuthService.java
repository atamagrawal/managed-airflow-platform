package com.airflow.platform.service;

import com.airflow.platform.config.PlatformSecurityProperties;
import com.airflow.platform.dto.AuthResponse;
import com.airflow.platform.dto.UserAccountResponse;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlatformSecurityProperties securityProperties;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;

    public AuthResponse login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String key = username.trim();
        Map<String, PlatformSecurityProperties.UserAccountProperties> users = securityProperties.getUsers();
        PlatformSecurityProperties.UserAccountProperties account = users.get(key);
        if (account == null) {
            for (Map.Entry<String, PlatformSecurityProperties.UserAccountProperties> e : users.entrySet()) {
                if (e.getKey().equalsIgnoreCase(key)) {
                    account = e.getValue();
                    key = e.getKey();
                    break;
                }
            }
        }
        if (account == null || account.getPassword() == null) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        List<String> roles = normalizeRoles(account.getRoles());
        boolean admin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
        String tenantScope = resolveHomeTenantId(account, admin);
        if (!admin) {
            assertTenantExistsForLogin(tenantScope);
        }

        String token = jwtService.createToken(key, roles, tenantScope);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(securityProperties.getJwtExpirationMs())
                .username(key)
                .roles(roles)
                .tenantScope(tenantScope)
                .admin(admin)
                .build();
    }

    /**
     * Lists users defined in configuration ({@code platform.security.users}). No passwords.
     */
    public List<UserAccountResponse> listConfiguredUsers() {
        return securityProperties.getUsers().entrySet().stream()
                .map(e -> {
                    PlatformSecurityProperties.UserAccountProperties account = e.getValue();
                    List<String> roles = normalizeRoles(account.getRoles());
                    boolean admin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
                    String tenantScope = resolveHomeTenantId(account, admin);
                    boolean usesDefault = !admin && !StringUtils.hasText(account.getTenantId());
                    return UserAccountResponse.builder()
                            .username(e.getKey())
                            .roles(roles)
                            .tenantScope(tenantScope)
                            .admin(admin)
                            .usesPlatformDefaultTenant(usesDefault)
                            .build();
                })
                .sorted(Comparator.comparing(UserAccountResponse::getUsername, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /**
     * JWT tenant claim for non-admins; {@code null} for platform admins (not scoped to one tenant).
     */
    private String resolveHomeTenantId(PlatformSecurityProperties.UserAccountProperties account, boolean admin) {
        if (admin) {
            return null;
        }
        if (StringUtils.hasText(account.getTenantId())) {
            return account.getTenantId().trim();
        }
        String fallback = securityProperties.getDefaultTenantIdForUsers();
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private void assertTenantExistsForLogin(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BadCredentialsException("Non-admin user has no home tenant configured");
        }
        try {
            tenantService.getTenantEntity(tenantId.trim());
        } catch (ResourceNotFoundException e) {
            throw new BadCredentialsException("Home tenant is not registered: " + tenantId.trim());
        }
    }

    private static List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("USER");
        }
        List<String> out = new ArrayList<>();
        for (String r : roles) {
            if (r == null || r.isBlank()) {
                continue;
            }
            out.add(r.trim().toUpperCase(Locale.ROOT));
        }
        if (out.isEmpty()) {
            return List.of("USER");
        }
        return List.copyOf(out);
    }
}
