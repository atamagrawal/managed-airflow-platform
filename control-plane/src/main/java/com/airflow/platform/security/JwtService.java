package com.airflow.platform.security;

import com.airflow.platform.config.PlatformSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtService {

    private final PlatformSecurityProperties securityProperties;

    public String createToken(String username, Collection<String> roles, String scopedTenantId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + securityProperties.getJwtExpirationMs());
        var builder = Jwts.builder()
                .subject(username)
                .claim("roles", List.copyOf(roles))
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey());
        if (scopedTenantId != null) {
            builder.claim("tenant", scopedTenantId);
        }
        return builder.compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        byte[] raw = securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            try {
                raw = MessageDigest.getInstance("SHA-256").digest(raw);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return Keys.hmacShaKeyFor(raw);
    }
}
