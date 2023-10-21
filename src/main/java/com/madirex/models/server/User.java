package com.madirex.models.server;

/**
 * Modelo de usuario
 */
public record User(long id, String username, String password, Role role) {
    /**
     * Enumerado de roles
     */
    public enum Role {
        ADMIN, USER
    }
}