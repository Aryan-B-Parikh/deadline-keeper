package com.deadlinekeeper.security;

import lombok.Getter;

import java.util.UUID;

@Getter
public class SupabaseUserPrincipal {
    private final UUID id;
    private final String email;

    public SupabaseUserPrincipal(UUID id, String email) {
        this.id = id;
        this.email = email;
    }
}
