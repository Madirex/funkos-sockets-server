package com.madirex.repositories.funko;

import com.madirex.models.funko.Funko;
import com.madirex.models.funko.Model;
import com.madirex.repositories.CRUDRepository;
import reactor.core.publisher.Flux;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Interfaz que define las operaciones CRUD de FunkoRepository
 */
public interface FunkoRepository extends CRUDRepository<Funko, UUID> {
    Flux<Funko> findByModel(Model model) throws SQLException;

    Flux<Funko> findByReleaseYear(Integer year) throws SQLException;
}
