package com.madirex.utils;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Esta clase contiene métodos para leer propiedades de un fichero
 */
public class ApplicationProperties {

    private static ApplicationProperties applicationPropertiesInstance;
    private final Properties databaseProperties;
    private final Properties serverProperties;

    /**
     * Constructor
     * Lee el fichero de propiedades y lo carga en un objeto Properties
     * Si no se puede leer el fichero, se muestra un mensaje de error en el log
     */
    private ApplicationProperties() {
        databaseProperties = new Properties();
        serverProperties = new Properties();
        try {
            databaseProperties.load(getClass().getClassLoader().getResourceAsStream("database.properties"));
            serverProperties.load(getClass().getClassLoader().getResourceAsStream("server.properties"));
        } catch (IOException ex) {
            Logger.getLogger(getClass().getName()).log(Level.ALL, ex,
                    () -> "IOException - Error al leer los ficheros de propiedades.");
        }
    }

    /**
     * SINGLETON - Este método devuelve una instancia de la clase ApplicationProperties
     *
     * @return Instancia de la clase ApplicationProperties
     */
    public static synchronized ApplicationProperties getInstance() {
        if (applicationPropertiesInstance == null) {
            applicationPropertiesInstance = new ApplicationProperties();
        }
        return applicationPropertiesInstance;
    }

    /**
     * Devuelve el valor de una clave del fichero de propiedades
     *
     * @param propertyType  Tipo de propiedad
     * @param keyName       Nombre de la clave
     * @param ifNotExistStr Valor por defecto si no existe la clave
     * @return Valor de la clave
     */
    public String readProperty(PropertyType propertyType, String keyName, String ifNotExistStr) {
        return switch (propertyType) {
            case DATABASE -> databaseProperties.getProperty(keyName, ifNotExistStr);
            case SERVER -> serverProperties.getProperty(keyName, ifNotExistStr);
        };
    }

    /**
     * Enum de tipos de propiedades
     */
    public enum PropertyType {
        DATABASE, SERVER
    }
}