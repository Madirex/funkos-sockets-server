package com.madirex.models.server;

/**
 * Clase que representa la respuesta del servidor
 */
public record Response(Status status, String content, String createdAt) {
    /**
     * Enumerado que representa el estado de la respuesta
     */
    public enum Status {
        OK, ERROR, BYE, TOKEN
    }
}