package com.madirex.services;

import com.madirex.exceptions.io.ReadCSVFailException;
import com.madirex.models.funko.Funko;
import com.madirex.services.io.CsvManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Clase CsvManagerTest
 */
class CsvManagerTest {

    private CsvManager csvManager;

    /**
     * Método que se ejecuta antes de cada test
     */
    @BeforeEach
    public void setUp() {
        csvManager = CsvManager.getInstance();
    }

    /**
     * Test para comprobar que se puede leer un archivo CSV
     *
     * @throws ReadCSVFailException Excepción al leer el archivo CSV
     */
    @Test
    void testFileToFunkoList() throws ReadCSVFailException {
        String filePath = "data/funkos-test.csv";
        Flux<Funko> funkoFlux = csvManager.fileToFunkoList(filePath);
        List<Funko> funkoList = funkoFlux.collectList().block();
        assert funkoList != null;
        assertEquals(90, funkoList.size());
    }

    /**
     * Test para comprobar que se lanza una excepción al leer un archivo CSV con un path inválido
     */
    @Test
    void testFileToFunkoListWithInvalidPath() {
        String invalidFilePath = "no_existe.csv";
        assertThrows(ReadCSVFailException.class, () -> csvManager.fileToFunkoList(invalidFilePath).collectList().block());
    }
}