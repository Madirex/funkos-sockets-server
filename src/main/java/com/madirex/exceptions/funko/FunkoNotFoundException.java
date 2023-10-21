package com.madirex.exceptions.funko;

/**
 * Excepción al no encontrar un Funko
 */
public class FunkoNotFoundException extends FunkoException {
    public FunkoNotFoundException(String message) {
        super("Funko/a no encontrado: " + message);
    }
}
