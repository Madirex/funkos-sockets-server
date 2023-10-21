package com.madirex.exceptions.funko;

/**
 * Excepción al no ser válido un Funko
 */
public class FunkoNotValidException extends FunkoException {
    /**
     * Constructor
     *
     * @param message mensaje de error
     */
    public FunkoNotValidException(String message) {
        super("Funko no válido: " + message);
    }
}
