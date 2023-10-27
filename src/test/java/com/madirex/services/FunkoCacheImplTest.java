package com.madirex.services;

import com.madirex.models.funko.Funko;
import com.madirex.services.cache.FunkoCacheImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clase de test para la clase FunkoCacheImpl
 */
class FunkoCacheImplTest {

    private FunkoCacheImpl cache;
    private final long secondsToClear = 1;

    /**
     * Inicializa la caché antes de cada test
     */
    @BeforeEach
    public void setUp() {
        cache = new FunkoCacheImpl(15, secondsToClear);
    }

    /**
     * Test put and get
     */
    @Test
    void testPutAndGet() {
        Funko funko = Funko.builder().build();
        cache.put("1", funko).block();
        assertEquals(funko, cache.get("1").block());
    }

    /**
     * Test remove
     */
    @Test
    void testRemove() {
        Funko funko = Funko.builder().build();
        cache.put("2", funko).block();
        cache.remove("2").block();
        assertNull(cache.get("2").block());
    }

    /**
     * Test shutdown
     */
    @Test
    void testShutdown() {
        cache.shutdown();
        assertTrue(cache.getCleaner().isShutdown());
    }

    /**
     * Test clear
     *
     * @throws InterruptedException si hay un error de interrupción
     */
    @Test
    void testClear() throws InterruptedException {
        Funko funko = Funko.builder().build();
        cache.put("1", funko).block();
        Thread.sleep(secondsToClear * 1000);
        cache.clear();
        assertNull(cache.get("1").block());
    }
}
