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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SupabaseJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SupabaseJwtAuthenticationFilter.class);
    private final SupabaseConfig supabaseConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile long cacheExpiry = 0;

    private static final long CACHE_TTL_MS = 3600000;
    private static final String JWKS_URL_TEMPLATE = "%s/auth/v1/.well-known/jwks.json";

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

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null,
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.info("JWT auth failed: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private Claims verifyAndParse(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid JWT: expected 3 parts");

        JsonNode header = parseBase64Json(parts[0]);
        String alg = header.has("alg") ? header.get("alg").asText() : null;
        String kid = header.has("kid") ? header.get("kid").asText() : null;

        if (!"ES256".equals(alg) && !"HS256".equals(alg)) {
            throw new SecurityException("Unsupported JWT algorithm: " + alg);
        }

        if ("ES256".equals(alg)) {
            if (kid == null) throw new SecurityException("ES256 token missing kid header");
            PublicKey publicKey = getECPublicKey(kid);
            if (publicKey == null) throw new SecurityException("No public key found for kid: " + kid);

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            validateClaims(claims);
            return claims;
        }

        Claims claims = verifyWithHMAC(token);
        validateClaims(claims);
        return claims;
    }

    private void validateClaims(Claims claims) {
        String issuer = claims.getIssuer();
        String expectedIssuer = supabaseConfig.getUrl() + "/auth/v1";

        if (issuer == null || issuer.isBlank()) {
            throw new SecurityException("JWT missing required issuer claim");
        }
        if (!issuer.equals(expectedIssuer)) {
            throw new SecurityException("Invalid JWT issuer: " + issuer);
        }

        Set<String> audience = claims.getAudience();
        String expectedAudience = supabaseConfig.getUrl()
                .replace("https://", "")
                .replace("http://", "")
                .split("\\.")[0];
        if (audience != null && !audience.isEmpty() && !audience.contains(expectedAudience)) {
            log.warn("JWT audience mismatch: expected={}, got={}", expectedAudience, audience);
        }

        if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
            throw new SecurityException("JWT has expired");
        }
    }

    private PublicKey getECPublicKey(String kid) throws Exception {
        if (System.currentTimeMillis() < cacheExpiry && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        String url = JWKS_URL_TEMPLATE.formatted(supabaseConfig.getUrl());
        JsonNode jwks = restTemplate.getForObject(url, JsonNode.class);
        if (jwks == null || !jwks.has("keys")) return null;

        for (JsonNode keyNode : jwks.get("keys")) {
            String k = keyNode.get("kid").asText();
            if ("EC".equals(keyNode.get("kty").asText())) {
                byte[] xBytes = Base64.getUrlDecoder().decode(
                        keyNode.get("x").asText() + "====".substring(0, (4 - keyNode.get("x").asText().length() % 4) % 4));
                byte[] yBytes = Base64.getUrlDecoder().decode(
                        keyNode.get("y").asText() + "====".substring(0, (4 - keyNode.get("y").asText().length() % 4) % 4));

                BigInteger x = new BigInteger(1, xBytes);
                BigInteger y = new BigInteger(1, yBytes);

                AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                params.init(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);

                ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(new java.security.spec.ECPoint(x, y), ecParameters);
                KeyFactory kf = KeyFactory.getInstance("EC");
                PublicKey pk = kf.generatePublic(pubKeySpec);

                keyCache.put(k, pk);
            }
        }

        cacheExpiry = System.currentTimeMillis() + CACHE_TTL_MS;
        return keyCache.get(kid);
    }

    private Claims verifyWithHMAC(String token) throws Exception {
        String secret = supabaseConfig.getJwtSecret();
        SecretKey key = new SecretKeySpec(decodeJwtSecret(secret), "HmacSHA256");
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private JsonNode parseBase64Json(String base64) throws IOException {
        String padded = base64 + "====".substring(0, (4 - base64.length() % 4) % 4);
        byte[] decoded = Base64.getUrlDecoder().decode(padded);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private static byte[] decodeJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return new byte[0];
        }
        if (secret.startsWith("sb_secret_")) {
            String raw = secret.substring("sb_secret_".length());
            try {
                String padded = raw;
                int rem = raw.length() % 4;
                if (rem != 0) {
                    padded = raw + "=".repeat(4 - rem);
                }
                return Base64.getUrlDecoder().decode(padded);
            } catch (IllegalArgumentException e) {
                return secret.getBytes(StandardCharsets.UTF_8);
            }
        }
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
