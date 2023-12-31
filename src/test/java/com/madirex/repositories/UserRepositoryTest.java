package com.madirex.repositories;

import com.madirex.models.server.User;
import com.madirex.repositories.server.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clase UserRepositoryTest
 */
class UserRepositoryTest {
    private UsersRepository usersRepository;

    /**
     * Método que se ejecuta antes de cada test
     */
    @BeforeEach
    public void setUp() {
        usersRepository = UsersRepository.getInstance();
    }

    /**
     * Test para comprobar que se puede agregar un usuario
     */
    @Test
    void testAddUser() {
        usersRepository.addUser(User.builder()
                .id(UUID.randomUUID().toString())
                .username("test")
                .password("123456")
                .role(User.Role.USER)
                .build()
        );
        var r = usersRepository.findByUsername("test");
        assertTrue(r.isPresent());
        assertAll("propiedades",
                () -> assertEquals("test", r.get().username()),
                () -> assertEquals("123456", r.get().password()),
                () -> assertEquals(User.Role.USER, r.get().role())
        );
    }

    /**
     * Test para comprobar que se puede obtener un usuario por su ID
     */
    @Test
    void testFindById() {
        var id = UUID.randomUUID().toString();
        usersRepository.addUser(User.builder()
                .id(id)
                .username("test")
                .password("123456")
                .role(User.Role.USER)
                .build()
        );
        var r = usersRepository.findById(id);
        assertTrue(r.isPresent());
        assertAll("propiedades",
                () -> assertEquals("test", r.get().username()),
                () -> assertEquals("123456", r.get().password()),
                () -> assertEquals(User.Role.USER, r.get().role())
        );
    }
}
