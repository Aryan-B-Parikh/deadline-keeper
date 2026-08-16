package com.deadlinekeeper.security;

import com.deadlinekeeper.config.SupabaseConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.Key;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

@Component
public class SupabaseJwtAuthenticationFilter extends OncePerRequestFilter {

    private final SupabaseConfig supabaseConfig;

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
                byte[] keyBytes = Base64.getDecoder().decode(supabaseConfig.getJwtSecret());
                Key key = new SecretKeySpec(keyBytes, "HmacSHA256");

                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

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
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
