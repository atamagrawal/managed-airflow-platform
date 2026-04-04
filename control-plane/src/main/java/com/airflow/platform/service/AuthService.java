package com.airflow.platform.service;

import com.airflow.platform.config.PlatformSecurityProperties;
import com.airflow.platform.dto.AuthResponse;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.PlatformUser;
import com.airflow.platform.repository.PlatformUserRepository;
import com.airflow.platform.security.AirflowBootstrapCrypto;
import com.airflow.platform.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final PlatformSecurityProperties securityProperties;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final PlatformUserRepository platformUserRepository;
    private final AirflowBootstrapCrypto airflowBootstrapCrypto;
    private final ObjectProvider<LocalAirflowFabUserSyncService> localAirflowFabUserSyncService;

    public AuthResponse login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String key = username.trim();

        var dbUser = platformUserRepository.findByUsernameIgnoreCaseAndEnabledIsTrue(key);
        if (dbUser.isPresent()) {
            PlatformUser u = dbUser.get();
            if (!passwordEncoder.matches(password, u.getPasswordHash())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            List<String> roles = PlatformUserService.parseRolesCsv(u.getRolesCsv());
            boolean admin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
            String tenantScope = resolveDatabaseUserTenantScope(u, admin);
            if (!admin) {
                assertTenantExistsForLogin(tenantScope);
            }
            String principal = u.getUsername();
            try {
                u.setAirflowBootstrapSecret(airflowBootstrapCrypto.encrypt(password));
                platformUserRepository.save(u);
            } catch (Exception e) {
                log.warn("Could not refresh Airflow bootstrap secret for {}: {}", principal, e.getMessage());
            }
            localAirflowFabUserSyncService.ifAvailable(
                    sync -> sync.syncUserOnLogin(principal, password, admin, tenantScope));
            return AuthResponse.builder()
                    .accessToken(jwtService.createToken(principal, roles, tenantScope))
                    .tokenType("Bearer")
                    .expiresInMs(securityProperties.getJwtExpirationMs())
                    .username(principal)
                    .roles(roles)
                    .tenantScope(tenantScope)
                    .admin(admin)
                    .build();
        }

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

        final String resolvedUsername = key;
        String token = jwtService.createToken(resolvedUsername, roles, tenantScope);

        localAirflowFabUserSyncService.ifAvailable(
                sync -> sync.syncUserOnLogin(resolvedUsername, password, admin, tenantScope));

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(securityProperties.getJwtExpirationMs())
                .username(resolvedUsername)
                .roles(roles)
                .tenantScope(tenantScope)
                .admin(admin)
                .build();
    }

    private String resolveDatabaseUserTenantScope(PlatformUser u, boolean admin) {
        if (admin) {
            return null;
        }
        if (StringUtils.hasText(u.getHomeTenantId())) {
            return u.getHomeTenantId().trim();
        }
        String fallback = securityProperties.getDefaultTenantIdForUsers();
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
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

    /**
     * Plain password for Airflow FAB {@code /auth/token} during UI handoff without re-prompting the user.
     * Uses the encrypted bootstrap secret saved for database users (refreshed on login), or YAML {@code {noop}} passwords.
     * YAML accounts with only a hashed password cannot be recovered server-side after JWT issuance.
     */
    public String resolveAirflowHandoffPassword(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Not authenticated");
        }
        String key = username.trim();

        var dbUser = platformUserRepository.findByUsernameIgnoreCaseAndEnabledIsTrue(key);
        if (dbUser.isPresent()) {
            PlatformUser u = dbUser.get();
            String cipher = u.getAirflowBootstrapSecret();
            if (!StringUtils.hasText(cipher)) {
                throw new IllegalArgumentException(
                        "Sign out and sign in once with your password to enable opening Airflow without typing it again.");
            }
            try {
                return airflowBootstrapCrypto.decrypt(cipher);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Stored sign-in secret is invalid. Sign out and sign in again, then try Open Airflow.");
            }
        }

        Map<String, PlatformSecurityProperties.UserAccountProperties> users = securityProperties.getUsers();
        PlatformSecurityProperties.UserAccountProperties account = users.get(key);
        if (account == null) {
            for (Map.Entry<String, PlatformSecurityProperties.UserAccountProperties> e : users.entrySet()) {
                if (e.getKey().equalsIgnoreCase(key)) {
                    account = e.getValue();
                    break;
                }
            }
        }
        if (account == null || account.getPassword() == null) {
            throw new IllegalArgumentException("Unknown platform user");
        }
        String pwd = account.getPassword();
        if (pwd.startsWith("{noop}")) {
            return pwd.substring("{noop}".length());
        }
        throw new IllegalArgumentException(
                "Automatic Airflow sign-in is not available for this YAML-only account (password is not stored in plain text). "
                        + "Use a database user from Users, or a {noop} password in configuration for local development.");
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
