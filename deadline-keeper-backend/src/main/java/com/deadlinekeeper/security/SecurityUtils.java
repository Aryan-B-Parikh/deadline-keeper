package com.deadlinekeeper.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SupabaseUserPrincipal principal) {
            return principal.getId();
        }
        throw new IllegalStateException("No authenticated user found");
    }
}
