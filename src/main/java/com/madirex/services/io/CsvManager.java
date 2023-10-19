package com.madirex.services.io;

import com.madirex.exceptions.ReadCSVFailException;
import com.madirex.models.funko.Funko;
import com.madirex.models.funko.Model;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Clase CsvManager que administra la exportación e importación de datos CSV
 */
public class CsvManager {

    private static CsvManager csvManagerInstance;

    /**
     * Constructor privado para evitar la creación de instancia
     * SINGLETON
     */
    private CsvManager() {
    }

    /**
     * Obtiene la instancia de CsvManager
     * SINGLETON
     *
     * @return Instancia de CsvManager
     */
    public static synchronized CsvManager getInstance() {
        if (csvManagerInstance == null) {
            csvManagerInstance = new CsvManager();
        }
        return csvManagerInstance;
    }

    /**
     * Lee un archivo CSV y lo convierte en lista de Funko
     *
     * @param path Ruta del archivo CSV
     * @return Lista de Funko
     * @throws ReadCSVFailException Excepción al leer el archivo CSV
     */
    public Flux<Funko> fileToFunkoList(String path) throws ReadCSVFailException {
        try {
            return Flux.fromStream(Files.lines(Paths.get(path)))
                    .skip(1)
                    .map(line -> {
                        String[] values = line.split(",");
                        return Funko.builder()
                                .cod(UUID.fromString(values[0].chars().limit(36).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                        .toString()))
                                .name(values[1])
                                .model(Model.valueOf(values[2]))
                                .price(Double.parseDouble(values[3]))
                                .releaseDate(LocalDate.parse(values[4]))
                                .build();
                    });
        } catch (IOException e) {
            throw new ReadCSVFailException(e.getMessage());
        }
    }
}
