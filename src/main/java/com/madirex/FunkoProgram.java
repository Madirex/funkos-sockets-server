package com.madirex;

import com.madirex.controllers.funko.FunkoControllerImpl;
import com.madirex.exceptions.FunkoNotSavedException;
import com.madirex.exceptions.FunkoNotValidException;
import com.madirex.exceptions.ReadCSVFailException;
import com.madirex.repositories.funko.FunkoRepositoryImpl;
import com.madirex.services.cache.FunkoCacheImpl;
import com.madirex.services.crud.funko.FunkoServiceImpl;
import com.madirex.services.crud.funko.IdGenerator;
import com.madirex.services.database.DatabaseManager;
import com.madirex.services.io.BackupService;
import com.madirex.services.io.CsvManager;
import com.madirex.services.notifications.FunkoNotificationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.File;
import java.sql.SQLException;

//TODO: ARREGLAR SONARLINT --> BACKUPSERVICE, UTILS Y FUNKOPROGRAM Y TAMBIÉN TODOS LOS TESTS

/**
 * Clase FunkoProgram que contiene el programa principal
 */
public class FunkoProgram {

    private static FunkoProgram funkoProgramInstance;
    private final Logger logger = LoggerFactory.getLogger(FunkoProgram.class);
    private FunkoControllerImpl controller;

    /**
     * Constructor privado para evitar la creación de instancia
     * SINGLETON
     */
    private FunkoProgram() {
        controller = FunkoControllerImpl.getInstance(FunkoServiceImpl
                .getInstance(FunkoRepositoryImpl.getInstance(IdGenerator.getInstance(), DatabaseManager.getInstance()),
                        new FunkoCacheImpl(15, 90),
                        BackupService.getInstance(), FunkoNotificationImpl.getInstance()));
    }

    /**
     * SINGLETON - Este método devuelve una instancia de la clase FunkoProgram
     *
     * @return Instancia de la clase FunkoProgram
     */
    public static synchronized FunkoProgram getInstance() {
        if (funkoProgramInstance == null) {
            funkoProgramInstance = new FunkoProgram();
        }
        return funkoProgramInstance;
    }

    /**
     * Inicia el programa
     */
    public void init() {
        logger.info("Programa de Funkos iniciado.");
        var loadAndUploadFunkos = loadFunkosFileAndInsertToDatabase("data" + File.separator + "funkos.csv");
        loadNotificationSystem();
        loadAndUploadFunkos.doFinally(signalType -> {
            controller.shutdown();
            logger.info("Programa de Funkos finalizado.");
        }).subscribe();
    }

    /**
     * Carga el sistema de notificaciones
     *
     * @return disposable
     */
    private Disposable loadNotificationSystem() {
        logger.info("Cargando sistema de notificaciones...");
        return controller.getNotifications().subscribe(
                notification -> {
                    String msg = "Notificación emitida";
                    switch (notification.getType()) {
                        case NEW:
                            msg = "🟢 Funko insertado: " + notification.getContent();
                            break;
                        case UPDATED:
                            msg = "🟠 Funko actualizado: " + notification.getContent();
                            break;
                        case DELETED:
                            msg = "🔴 Funko eliminado: " + notification.getContent();
                            break;
                    }
                    logger.info(msg);
                },
                error -> logger.error("Se ha producido un error: " + error),
                () -> logger.info("Sistema de notificaciones cargado.")
        );
    }


    /**
     * Lee un archivo CSV y lo inserta en la base de datos de manera asíncrona
     *
     * @param path Ruta del archivo CSV
     * @return Mono<Void>
     */
    public Mono<Void> loadFunkosFileAndInsertToDatabase(String path) {
        CsvManager csvManager = CsvManager.getInstance();
        try {
            return csvManager.fileToFunkoList(path)
                    .flatMap(funko -> {
                        try {
                            return controller.save(funko)
                                    .onErrorResume(e -> {
                                        String str = "Error al insertar el Funko en la base de datos: " + e.getMessage();
                                        logger.error(str);
                                        return Mono.empty();
                                    });
                        } catch (SQLException | FunkoNotSavedException | FunkoNotValidException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .then();
        } catch (ReadCSVFailException e) {
            throw new RuntimeException(e);
        }
    }

}
