package com.madirex.controllers.funko;

import com.madirex.controllers.BaseController;
import com.madirex.models.funko.Funko;
import com.madirex.models.funko.Model;
import reactor.core.publisher.Flux;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Controlador de Funko
 */
public interface FunkoController extends BaseController<Funko, UUID> {
    Flux<Funko> findByModel(Model model) throws SQLException;

    Flux<Funko> findByReleaseYear(Integer year) throws SQLException;
}
