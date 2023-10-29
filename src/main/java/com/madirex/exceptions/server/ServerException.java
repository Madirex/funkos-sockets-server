package com.madirex.exceptions.server;

/**
 * Excepción del servidor
 */
public class ServerException extends Exception {
    /**
     * Constructor
     *
     * @param message mensaje de error
     */
    public ServerException(String message) {
        super(message);
    }
}