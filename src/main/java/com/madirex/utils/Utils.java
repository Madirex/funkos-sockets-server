package com.madirex.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

/**
 * Clase Utils que contiene métodos útiles para la aplicación
 */
public class Utils {

    private static Utils utilsInstance;
    private final Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * Constructor privado de la clase Utils
     */
    private Utils() {
    }

    /**
     * Devuelve una instancia de la clase Utils
     *
     * @return Instancia de la clase Utils
     */
    public static synchronized Utils getInstance() {
        if (utilsInstance == null) {
            utilsInstance = new Utils();
        }
        return utilsInstance;
    }

    /**
     * Devuelve un String con el formato de moneda de España
     *
     * @param dbl cantidad de tipo double
     * @return Moneda con formato de España
     */
    public String doubleToESLocal(double dbl) {
        return String.format("%,.2f", dbl).replace(".", ",");
    }

    /**
     * Devuelve los bytes de un archivo
     *
     * @param dataFile Archivo del que se quieren obtener los bytes
     * @return Bytes del archivo
     */
    public Optional<byte[]> getFileBytes(File dataFile) {
        try {
            return Optional.of(Files.readAllBytes(dataFile.toPath()));
        } catch (IOException e) {
            String stre = "Error al leer el archivo: " + e.getMessage();
            logger.error(stre);
            return Optional.empty();
        }
    }

    /**
     * Escribe un String en un archivo
     *
     * @param dest Ruta del archivo
     * @param json String a escribir
     */
    public void writeString(String dest, String json) {
        try {
            Files.writeString(new File(dest).toPath(), json);
        } catch (IOException e) {
            String stre = "Error al escribir el archivo: " + e.getMessage();
            logger.error(stre);
        }
    }
}
