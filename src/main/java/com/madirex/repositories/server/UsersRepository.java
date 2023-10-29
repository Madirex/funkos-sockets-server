package com.madirex.repositories.server;

import com.madirex.models.server.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de usuarios
 */
public class UsersRepository {
    private static UsersRepository instance = null;
    private final List<User> users;

    /**
     * Constructor de la clase
     */
    private UsersRepository() {
        users = new ArrayList<>();
    }

    /**
     * Devuelve la instancia de la clase
     *
     * @return Instancia de la clase
     */
    public static synchronized UsersRepository getInstance() {
        if (instance == null) {
            instance = new UsersRepository();
        }
        return instance;
    }

    /**
     * Añade un usuario a la lista de usuarios
     *
     * @param user usuario a añadir
     */
    public synchronized void addUser(User user) {
        instance.users.add(user);
    }

    /**
     * Busca por nombre de usuario el usuario
     *
     * @return Usuario
     */
    public Optional<User> findByUsername(String username) {
        return users.stream()
                .filter(user -> user.username().equals(username))
                .findFirst();
    }

    /**
     * Busca por ID el usuario
     *
     * @return Usuario
     */
    public Optional<User> findById(String id) {
        return users.stream()
                .filter(user -> user.id().equalsIgnoreCase(id.replace("\"", "")))
                .findFirst();
    }
}