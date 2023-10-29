package com.madirex.models.server;

import lombok.Builder;

/**
 * Modelo de usuario
 */
@Builder
public record User(String id, String username, String password, Role role) {
    /**
     * Enumerado de roles
     */
    public enum Role {
        ADMIN, USER
    }
}