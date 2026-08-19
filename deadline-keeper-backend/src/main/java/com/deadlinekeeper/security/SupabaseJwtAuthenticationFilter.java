package com.deadlinekeeper.security;

import com.deadlinekeeper.config.SupabaseConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SupabaseJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SupabaseJwtAuthenticationFilter.class);
    private static final long CACHE_TTL_MS = 3_600_000L;
    private static final String JWKS_URL_TEMPLATE = "%s/auth/v1/.well-known/jwks.json";

    private final SupabaseConfig supabaseConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile long cacheExpiry;

    public SupabaseJwtAuthenticationFilter(SupabaseConfig supabaseConfig) {
        this.supabaseConfig = supabaseConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = verifyAndParse(token);
                String userId = claims.getSubject();
                if (userId != null) {
                    SupabaseUserPrincipal principal = new SupabaseUserPrincipal(
                            UUID.fromString(userId),
                            (String) claims.get("email"));
                    var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            principal, null, Collections.singletonList(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.debug("JWT authentication failed: {}", e.getMessage());
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private Claims verifyAndParse(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new SecurityException("Invalid JWT");

        JsonNode header = parseBase64Json(parts[0]);
        String algorithm = header.has("alg") ? header.get("alg").asText() : null;
        String kid = header.has("kid") ? header.get("kid").asText() : null;

        if (!"ES256".equals(algorithm) && !"HS256".equals(algorithm)) {
            throw new SecurityException("Unsupported JWT algorithm");
        }

        Claims claims;
        if ("ES256".equals(algorithm)) {
            if (kid == null || kid.isBlank()) throw new SecurityException("JWT missing kid header");
            PublicKey publicKey = getECPublicKey(kid);
            if (publicKey == null) throw new SecurityException("JWT signing key not found");
            claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
        } else {
            claims = verifyWithHMAC(token);
        }

        validateClaims(claims);
        return claims;
    }

    private void validateClaims(Claims claims) {
        String baseUrl = normalizeBaseUrl(supabaseConfig.getUrl());
        String expectedIssuer = baseUrl + "/auth/v1";

        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new SecurityException("Invalid JWT issuer");
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || audience.isEmpty() || !audience.contains("authenticated")) {
            throw new SecurityException("Invalid JWT audience");
        }

        Date expiration = claims.getExpiration();
        if (expiration == null || !expiration.after(new Date())) {
            throw new SecurityException("JWT is expired or missing expiration");
        }
    }

    private PublicKey getECPublicKey(String kid) throws Exception {
        if (System.currentTimeMillis() < cacheExpiry && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        String url = JWKS_URL_TEMPLATE.formatted(normalizeBaseUrl(supabaseConfig.getUrl()));
        JsonNode jwks = restTemplate.getForObject(url, JsonNode.class);
        if (jwks == null || !jwks.has("keys")) return null;

        for (JsonNode keyNode : jwks.get("keys")) {
            if (!"EC".equals(keyNode.path("kty").asText())) continue;
            String keyId = keyNode.path("kid").asText();
            if (keyId.isBlank()) continue;

            byte[] xBytes = decodeBase64Url(keyNode.path("x").asText());
            byte[] yBytes = decodeBase64Url(keyNode.path("y").asText());
            BigInteger x = new BigInteger(1, xBytes);
            BigInteger y = new BigInteger(1, yBytes);

            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);
            ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(x, y), ecParameters);
            PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(spec);
            keyCache.put(keyId, publicKey);
        }

        cacheExpiry = System.currentTimeMillis() + CACHE_TTL_MS;
        return keyCache.get(kid);
    }

    private Claims verifyWithHMAC(String token) {
        String secret = supabaseConfig.getJwtSecret();
        if (secret == null || secret.isBlank()) throw new SecurityException("JWT secret is not configured");
        SecretKey key = new SecretKeySpec(decodeJwtSecret(secret), "HmacSHA256");
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private JsonNode parseBase64Json(String value) throws IOException {
        return objectMapper.readTree(new String(decodeBase64Url(value), StandardCharsets.UTF_8));
    }

    private static byte[] decodeBase64Url(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Invalid base64url value");
        int padding = (4 - value.length() % 4) % 4;
        return Base64.getUrlDecoder().decode(value + "=".repeat(padding));
    }

    private static byte[] decodeJwtSecret(String secret) {
        if (secret.startsWith("sb_secret_")) return decodeBase64Url(secret.substring("sb_secret_".length()));
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) throw new SecurityException("Supabase URL is not configured");
        return url.replaceAll("/+$", "");
    }
}
