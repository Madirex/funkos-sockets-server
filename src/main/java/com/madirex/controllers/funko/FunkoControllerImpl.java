package com.madirex.controllers.funko;

import com.madirex.exceptions.funko.FunkoNotSavedException;
import com.madirex.exceptions.funko.FunkoNotValidException;
import com.madirex.models.Notification;
import com.madirex.models.funko.Funko;
import com.madirex.models.funko.Model;
import com.madirex.services.crud.funko.FunkoServiceImpl;
import com.madirex.validators.FunkoValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Controlador de Funko
 */
public class FunkoControllerImpl implements FunkoController {
    private static FunkoControllerImpl funkoControllerImplInstance;
    private final Logger logger = LoggerFactory.getLogger(FunkoControllerImpl.class);

    private final FunkoServiceImpl funkoService;

    /**
     * Constructor
     *
     * @param funkoService servicio de Funko
     */
    private FunkoControllerImpl(FunkoServiceImpl funkoService) {
        this.funkoService = funkoService;
    }

    /**
     * Constructor de la clase
     *
     * @param funkoService servicio de Funko
     */
    public static synchronized FunkoControllerImpl getInstance(FunkoServiceImpl funkoService) {
        if (funkoControllerImplInstance == null) {
            funkoControllerImplInstance = new FunkoControllerImpl(funkoService);
        }
        return funkoControllerImplInstance;
    }


    /**
     * Busca todos los Funkos
     *
     * @return Funkos encontrados
     */
    @Override
    public Flux<Funko> findAll() {
        logger.debug("FindAll");
        return funkoService.findAll();
    }

    /**
     * Busca un Funko por id
     *
     * @param id id del Funko
     * @return Funko encontrado
     */
    @Override
    public Mono<Funko> findById(UUID id) {
        String msg = "FindById " + id;
        logger.debug(msg);
        return funkoService.findById(id);
    }

    /**
     * Busca Funkos por modelo
     *
     * @param model modelo del Funko
     * @return Funkos encontrados
     */
    @Override
    public Flux<Funko> findByModel(Model model) {
        String msg = "FindByModel " + model;
        logger.debug(msg);
        return funkoService.findByModel(model);
    }

    /**
     * Busca Funkos por año de lanzamiento
     *
     * @param year año de lanzamiento
     * @return Funkos encontrados
     */
    @Override
    public Flux<Funko> findByReleaseYear(Integer year) {
        String msg = "FindByReleaseYear " + year;
        logger.debug(msg);
        return funkoService.findByReleaseYear(year);
    }

    /**
     * Guarda un Funko
     *
     * @param funko Funko a guardar
     * @return Funko guardado
     * @throws SQLException           si hay un error en la base de datos
     * @throws FunkoNotSavedException si no se guarda el Funko
     * @throws FunkoNotValidException si el Funko no es válido
     */
    @Override
    public Mono<Funko> save(Funko funko) throws SQLException, FunkoNotSavedException, FunkoNotValidException {
        String msg = "Save " + funko;
        logger.debug(msg);
        FunkoValidator.validate(funko);
        return funkoService.save(funko);
    }

    /**
     * Actualiza un Funko
     *
     * @param id    id del Funko
     * @param funko Funko a actualizar
     * @return Funko actualizado
     * @throws FunkoNotValidException si el Funko no es válido
     */
    @Override
    public Mono<Funko> update(UUID id, Funko funko) throws FunkoNotValidException {
        String msg = "Update " + funko;
        logger.debug(msg);
        FunkoValidator.validate(funko);
        return funkoService.update(id, funko);
    }

    /**
     * Elimina un Funko
     *
     * @param id id del Funko
     * @return Funko eliminado
     */
    @Override
    public Mono<Funko> delete(UUID id) {
        String msg = "Delete " + id;
        logger.debug(msg);
        return funkoService.delete(id);
    }

    /**
     * Exporta los datos de la base de datos a un archivo JSON
     *
     * @param url      url de la base de datos
     * @param fileName nombre del archivo
     */
    public Mono<Void> exportData(String url, String fileName) {
        return findAll()
                .collectList()
                .flatMap(dataList -> {
                    try {
                        var exp = funkoService.exportData(url, fileName, dataList);
                        return exp.onErrorResume(e -> {
                            logger.error("Error al exportar los datos: ", e);
                            return Mono.empty();
                        });
                    } catch (SQLException e) {
                        logger.error("Error al exportar los datos: ", e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * Importa los datos de un archivo JSON a la base de datos
     *
     * @param url      url de la base de datos
     * @param fileName nombre del archivo
     */
    public Flux<Funko> importData(String url, String fileName) {
        return funkoService.importData(url, fileName);
    }

    /**
     * Cierra el caché
     */
    public void shutdown() {
        funkoService.shutdown();
    }

    /**
     * Recone las notificaciones
     *
     * @return Notificaciones
     */
    public Flux<Notification<Funko>> getNotifications() {
        return funkoService.getNotifications();
    }
}