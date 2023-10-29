package com.madirex.models.server;

/**
 * Clase que representa una petición
 */
public record Request(Type type, String content, String token, String createdAt) {
    /**
     * Enumerado que representa el tipo de petición
     */
    public enum Type {
        LOGIN, EXIT, GETALL, GETBYID, GETBYMODEL, GETBYRELEASEYEAR, INSERT, UPDATE, DELETE
    }
}