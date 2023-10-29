package com.madirex.exceptions.funko;

/**
 * Excepción al no encontrar un Funko
 */
public class FunkoNotFoundException extends FunkoException {
    /**
     * Constructor
     *
     * @param message mensaje de error
     */
    public FunkoNotFoundException(String message) {
        super("Funko/a no encontrado: " + message);
    }
}
