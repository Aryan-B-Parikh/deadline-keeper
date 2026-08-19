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

@Component
public class SupabaseJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SupabaseJwtAuthenticationFilter.class);
    private final SupabaseConfig supabaseConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String cachedKid = null;
    private volatile PublicKey cachedPublicKey = null;
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
        if (parts.length < 2) throw new IllegalArgumentException("Invalid JWT");

        JsonNode header = objectMapper.readTree(
                new String(Base64.getUrlDecoder().decode(parts[0] + "====".substring(0, (4 - parts[0].length() % 4) % 4)), StandardCharsets.UTF_8));

        String alg = header.has("alg") ? header.get("alg").asText() : null;
        String kid = header.has("kid") ? header.get("kid").asText() : null;
        log.info("JWT alg={}, kid={}", alg, kid);

        if ("ES256".equals(alg) && kid != null) {
            PublicKey publicKey = getECPublicKey(kid);
            if (publicKey != null) {
                log.info("Using EC public key for kid={}", kid);
                return Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            }
            log.warn("No EC public key found for kid={}", kid);
        }

        return verifyWithHMAC(token);
    }

    private PublicKey getECPublicKey(String kid) throws Exception {
        if (kid.equals(cachedKid) && cachedPublicKey != null && System.currentTimeMillis() < cacheExpiry) {
            return cachedPublicKey;
        }

        String url = JWKS_URL_TEMPLATE.formatted(supabaseConfig.getUrl());
        JsonNode jwks = restTemplate.getForObject(url, JsonNode.class);
        if (jwks == null || !jwks.has("keys")) return null;

        for (JsonNode keyNode : jwks.get("keys")) {
            if (kid.equals(keyNode.get("kid").asText()) && "EC".equals(keyNode.get("kty").asText())) {
                byte[] xBytes = Base64.getUrlDecoder().decode(keyNode.get("x").asText() + "====".substring(0, (4 - keyNode.get("x").asText().length() % 4) % 4));
                byte[] yBytes = Base64.getUrlDecoder().decode(keyNode.get("y").asText() + "====".substring(0, (4 - keyNode.get("y").asText().length() % 4) % 4));

                BigInteger x = new BigInteger(1, xBytes);
                BigInteger y = new BigInteger(1, yBytes);

                AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                params.init(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);

                ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(new java.security.spec.ECPoint(x, y), ecParameters);

                KeyFactory kf = KeyFactory.getInstance("EC");
                PublicKey pk = kf.generatePublic(pubKeySpec);

                cachedKid = kid;
                cachedPublicKey = pk;
                cacheExpiry = System.currentTimeMillis() + CACHE_TTL_MS;
                return pk;
            }
        }
        return null;
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
