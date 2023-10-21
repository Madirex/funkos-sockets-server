package com.madirex.exceptions.io;

/**
 * Excepción de directorio incorrecto
 */
public class DirectoryException extends Exception {
    /**
     * Constructor
     *
     * @param message mensaje de error
     */
    public DirectoryException(String message) {
        super("Error | El directorio no existe: " + message);
    }
}
