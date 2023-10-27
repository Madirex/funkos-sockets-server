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

class CsvManagerTest {

    private CsvManager csvManager;

    @BeforeEach
    public void setUp() {
        csvManager = CsvManager.getInstance();
    }

    @Test
    void testFileToFunkoList() throws ReadCSVFailException {
        String filePath = "data/funkos-test.csv";
        Flux<Funko> funkoFlux = csvManager.fileToFunkoList(filePath);
        List<Funko> funkoList = funkoFlux.collectList().block();
        assert funkoList != null;
        assertEquals(90, funkoList.size());
    }

    @Test
    void testFileToFunkoListWithInvalidPath() {
        String invalidFilePath = "no_existe.csv";
        assertThrows(ReadCSVFailException.class, () -> {
            csvManager.fileToFunkoList(invalidFilePath).collectList().block();
        });
    }
}