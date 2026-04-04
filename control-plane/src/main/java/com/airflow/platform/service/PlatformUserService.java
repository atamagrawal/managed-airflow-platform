package com.airflow.platform.service;

import com.airflow.platform.config.PlatformSecurityProperties;
import com.airflow.platform.dto.PlatformUserCreateRequest;
import com.airflow.platform.dto.UserAccountResponse;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.PlatformUser;
import com.airflow.platform.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformSecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;

    @Transactional
    public UserAccountResponse createUser(PlatformUserCreateRequest request) {
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Username is required");
        }
        if (platformUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (usernameReservedByConfiguration(username)) {
            throw new IllegalArgumentException(
                    "Username is already defined in platform.security.users; choose another name or remove it from configuration.");
        }

        List<String> roles = normalizeRoles(request.getRoles());
        boolean admin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
        String homeTenantId = StringUtils.hasText(request.getTenantId()) ? request.getTenantId().trim() : null;
        if (!admin) {
            String effective = StringUtils.hasText(homeTenantId)
                    ? homeTenantId
                    : securityProperties.getDefaultTenantIdForUsers();
            if (!StringUtils.hasText(effective)) {
                throw new IllegalArgumentException("Non-admin users need a home tenant or platform.default-tenant-id-for-users must be set");
            }
            try {
                tenantService.getTenantEntity(effective.trim());
            } catch (ResourceNotFoundException e) {
                throw new IllegalArgumentException("Tenant does not exist: " + effective.trim());
            }
        }

        PlatformUser entity = new PlatformUser();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setRolesCsv(String.join(",", roles));
        entity.setHomeTenantId(admin ? null : homeTenantId);
        entity.setEnabled(true);
        entity = platformUserRepository.save(entity);

        return toResponse(entity);
    }

    @Transactional
    public void deleteUser(Long id) {
        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: id=" + id));

        List<String> roles = parseRolesCsv(user.getRolesCsv());
        boolean isAdmin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
        if (isAdmin) {
            long otherDbAdmins = platformUserRepository.findAll().stream()
                    .filter(PlatformUser::isEnabled)
                    .filter(u -> !u.getId().equals(id))
                    .filter(u -> parseRolesCsv(u.getRolesCsv()).stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r)))
                    .count();
            long yamlAdmins = countConfigurationAdmins();
            if (otherDbAdmins + yamlAdmins == 0) {
                throw new IllegalArgumentException("Cannot remove the last platform administrator");
            }
        }

        platformUserRepository.delete(user);
    }

    public List<UserAccountResponse> listMergedWithConfiguration() {
        Set<String> seen = new LinkedHashSet<>();
        List<UserAccountResponse> out = new ArrayList<>();

        for (PlatformUser u : platformUserRepository.findAll()) {
            seen.add(u.getUsername().toLowerCase(Locale.ROOT));
            out.add(toResponse(u));
        }

        for (Map.Entry<String, PlatformSecurityProperties.UserAccountProperties> e : securityProperties.getUsers().entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            PlatformSecurityProperties.UserAccountProperties account = e.getValue();
            List<String> roles = normalizeRoles(account.getRoles());
            boolean admin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
            String tenantScope = resolveConfigTenantScope(account, admin);
            boolean usesDefault = !admin && !StringUtils.hasText(account.getTenantId());
            out.add(UserAccountResponse.builder()
                    .username(e.getKey())
                    .roles(roles)
                    .tenantScope(tenantScope)
                    .admin(admin)
                    .usesPlatformDefaultTenant(usesDefault)
                    .source(UserAccountResponse.SOURCE_CONFIGURATION)
                    .build());
        }

        out.sort(Comparator.comparing(UserAccountResponse::getUsername, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private UserAccountResponse toResponse(PlatformUser u) {
        List<String> roles = parseRolesCsv(u.getRolesCsv());
        boolean admin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r));
        String tenantScope;
        if (admin) {
            tenantScope = null;
        } else if (StringUtils.hasText(u.getHomeTenantId())) {
            tenantScope = u.getHomeTenantId().trim();
        } else {
            String fallback = securityProperties.getDefaultTenantIdForUsers();
            tenantScope = StringUtils.hasText(fallback) ? fallback.trim() : null;
        }
        boolean usesDefault = !admin && !StringUtils.hasText(u.getHomeTenantId());
        return UserAccountResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .roles(roles)
                .tenantScope(tenantScope)
                .admin(admin)
                .usesPlatformDefaultTenant(usesDefault)
                .source(UserAccountResponse.SOURCE_DATABASE)
                .build();
    }

    private boolean usernameReservedByConfiguration(String usernameLower) {
        for (String key : securityProperties.getUsers().keySet()) {
            if (key != null && key.toLowerCase(Locale.ROOT).equals(usernameLower)) {
                return true;
            }
        }
        return false;
    }

    private long countConfigurationAdmins() {
        return securityProperties.getUsers().values().stream()
                .map(a -> normalizeRoles(a.getRoles()))
                .filter(r -> r.stream().anyMatch(x -> "ADMIN".equalsIgnoreCase(x)))
                .count();
    }

    private String resolveConfigTenantScope(PlatformSecurityProperties.UserAccountProperties account, boolean admin) {
        if (admin) {
            return null;
        }
        if (StringUtils.hasText(account.getTenantId())) {
            return account.getTenantId().trim();
        }
        String fallback = securityProperties.getDefaultTenantIdForUsers();
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    public static List<String> parseRolesCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of("USER");
        }
        List<String> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            if (StringUtils.hasText(p)) {
                out.add(p.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? List.of("USER") : List.copyOf(out);
    }

    private static List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("USER");
        }
        List<String> out = roles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
        if (out.isEmpty()) {
            return List.of("USER");
        }
        return out;
    }
}
