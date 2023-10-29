package com.madirex.exceptions.funko;

/**
 * Excepción al no eliminar un Funko
 */
public class FunkoNotRemovedException extends FunkoException {
    /**
     * Constructor
     *
     * @param message mensaje de error
     */
    public FunkoNotRemovedException(String message) {
        super("Funko no eliminado: " + message);
    }
}
