package com.madirex;

import com.madirex.controllers.FunkoController;
import com.madirex.exceptions.*;
import com.madirex.models.funko.Funko;
import com.madirex.models.funko.Model;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

//TODO: ARREGLAR SONARLINT --> BACKUPSERVICE, UTILS Y FUNKOPROGRAM

/**
 * Clase FunkoProgram que contiene el programa principal
 */
public class FunkoProgram {

    private static FunkoProgram funkoProgramInstance;
    private final Logger logger = LoggerFactory.getLogger(FunkoProgram.class);
    private FunkoController controller;

    /**
     * Constructor privado para evitar la creación de instancia
     * SINGLETON
     */
    private FunkoProgram() {
        controller = FunkoController.getInstance(FunkoServiceImpl
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
        loadAndUploadFunkos.block();
        var serviceExceptionMono = callAllServiceExceptionMethods();
        Mono<Void> serviceMono = callAllServiceMethods();
        Mono<Void> queriesMono = databaseQueries();
        Mono<Void> allOperationsMono = Mono.when(serviceMono, serviceExceptionMono, queriesMono);
        allOperationsMono.doFinally(signalType -> {
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
     * Lanzar excepciones de los métodos service
     *
     * @return Devuelve los datos
     */
    private Mono<Void> callAllServiceExceptionMethods() {
        logger.info("🔴 Probando casos incorrectos 🔴");

        Mono<Void> s1 = printFindById(UUID.fromString("569689dd-b76b-465b-aa32-a6c46acd38fd"), false).then();
        Mono<Void> s2 = printFindByName("NoExiste", false).then();

        Funko funko = Funko.builder()
                .name("MadiFunko2")
                .model(Model.OTROS)
                .price(-42)
                .releaseDate(LocalDate.now())
                .build();

        Mono<Void> s3 = printSave(funko, false).then();
        Mono<Void> s4 = printUpdate("One Piece Luffy", "", false).then();
        Mono<Void> s5 = printDelete("NoExiste", false).then();

        return Mono.when(s1, s2, s3, s4, s5);
    }


    /**
     * Llama a todos los métodos de la clase FunkoService
     *
     * @return Devuelve los datos
     */
    private Mono<Void> callAllServiceMethods() {
        logger.info("🟢 Probando casos correctos 🟢");

        Mono<Void> s1 = printFindAll().then();
        Mono<Void> s2 = printFindById(UUID.fromString("3b6c6f58-7c6b-434b-82ab-01b2d6e4434a"), true).then();
        Mono<Void> s3 = printFindByName("Doctor Who Tardis", true).then();

        Funko funko = Funko.builder()
                .name("MadiFunko")
                .model(Model.OTROS)
                .price(42)
                .releaseDate(LocalDate.now())
                .build();

        Mono<Void> s4 = printSave(funko, true).then();
        Mono<Void> s5 = printUpdate("MadiFunko", "MadiFunkoModified", true).then();
        Mono<Void> s6 = printDelete("MadiFunkoModified", true).then();
        Mono<Void> s7 = doBackupAndPrint("data").then();
        Mono<Void> s8 = loadBackupAndPrint("data").then();

        return Mono.when(s1, s2, s3, s4, s5, s6, s7, s8);
    }

    /**
     * Carga una copia de seguridad y la imprime
     *
     * @param rootFolderName Nombre de la carpeta raíz
     * @return Devuelve los datos
     */
    private Mono<Void> loadBackupAndPrint(String rootFolderName) {
        var imported = controller.importData(System.getProperty("user.dir") + File.separator +
                rootFolderName, "backup.json");
        imported.then().block();
        return imported
                .doOnNext(funko -> {
                    logger.info("🟢 Copia de seguridad...");
                    logger.info(funko.toString());
                })
                .then();
    }

    /**
     * Consultas a la base de datos
     *
     * @return Devuelve los datos
     */
    private Mono<Void> databaseQueries() {
        var q1 = printExpensiveFunko();
        var q2 = printAvgPriceOfFunkos();
        var q3 = printFunkosGroupedByModels();
        var q4 = printNumberOfFunkosByModels();
        var q5 = printFunkosReleasedIn(2023);
        var q6 = printNumberOfFunkosOfName("Stitch");
        var q7 = printListOfFunkosOfName("Stitch");
        return Mono.when(q1, q2, q3, q4, q5, q6, q7);
    }

    /**
     * Imprime una lista de Funkos que contengan el nombre pasado por parámetro
     *
     * @param name Nombre del Funko
     * @return Devuelve los datos
     */
    private Flux<Funko> printListOfFunkosOfName(String name) {
        AtomicBoolean almostOne = new AtomicBoolean(false);
        return controller.listOfFunkosByName(name)
                .doOnEach(funko -> {
                    String str = "";
                    if (funko.hasValue()) {
                        str = "🔵 Funko encontrado: " + funko.get();
                        almostOne.set(true);
                        logger.info(str);
                    } else {
                        if (!almostOne.get()) {
                            str = "🔵 No se encontró un Funko con el nombre: " + name;
                            logger.info(str);
                        }
                    }
                })
                .onErrorResume(e -> {
                    String str = "Error: " + e;
                    logger.error(str);
                    return Mono.empty();
                });
    }

    /**
     * Imprime el número de Funkos por nombre
     *
     * @param name nombre
     * @return Número
     */
    private Mono<Long> printNumberOfFunkosOfName(String name) {
        return controller.listOfFunkosByName(name)
                .count()
                .doOnSuccess(count -> {
                    String str = "🔵 Número de Funkos de " + name + ": " + count;
                    logger.info(str);
                })
                .onErrorResume(e -> {
                    String str = "Funkos no encontrados: " + e;
                    logger.error(str);
                    return Mono.empty();
                });
    }

    /**
     * Imprime los Funkos lanzados en year
     *
     * @param year Año
     * @return Devuelve los datos
     */
    private Flux<Funko> printFunkosReleasedIn(int year) {
        return controller.getFunkoReleasedIn(year)
                .doOnNext(filteredFunko -> {
                    String str = "🔵 Funko lanzado en el año " + year + ": " + filteredFunko;
                    logger.info(str);
                });
    }


    /**
     * Imprime el número de Funkos por modelo
     *
     * @return Devuelve los datos
     */
    private Mono<Object> printNumberOfFunkosByModels() {
        return controller.getNumberFunkosByModelMap()
                .flatMap(modelsMap -> {
                    logger.info("🔵 Número de Funkos por modelos...");
                    modelsMap.forEach((model, count) -> {
                        String str = "🔵 " + model + " -> " + count.size();
                        logger.info(str);
                    });
                    return Mono.empty();
                });
    }

    /**
     * Imprime los Funkos agrupados por modelos
     *
     * @return Datos
     */
    private Mono<Object> printFunkosGroupedByModels() {
        return controller.getFunkoGroupedByModels()
                .flatMap(modelToFunKoMap -> {
                    logger.info("🔵 Funkos agrupados por modelos...");

                    modelToFunKoMap.forEach((model, funkoList) -> {
                        String str = "\n🔵 Modelo: " + model;
                        logger.info(str);
                        funkoList.forEach(funko -> logger.info(funko.toString()));
                    });

                    return Mono.empty();
                });
    }


    /**
     * Imprime la media de precio de los Funkos
     *
     * @return Datos
     */
    private Mono<Double> printAvgPriceOfFunkos() {
        return controller.getAvgPriceOfFunko()
                .doOnSuccess(averagePrice -> logger.info("🔵 Media de precio de Funkos: " + String.format("%.2f", averagePrice)))
                .onErrorResume(e -> {
                    String str = "Fallo al calcular la media de precio de Funkos: " + e;
                    logger.error(str);
                    return Mono.empty();
                });
    }

    /**
     * Imprime el Funko más caro
     *
     * @return Datos
     */
    private Mono<Void> printExpensiveFunko() {
        return controller.getExpensiveFunko()
                .flatMap(expensiveFunko -> {
                    logger.info("🔵 Funko más caro: " + expensiveFunko.toString());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Elimina un Funko y lo imprime
     *
     * @param name      Nombre del Funko
     * @param isCorrect Si es un caso correcto
     * @return Datos
     */
    private Flux<Object> printDelete(String name, boolean isCorrect) {
        return Flux.defer(() -> {
            try {
                return controller.findByName(name)
                        .flatMap(funko -> {
                            if (funko != null) {
                                var cod = funko.getCod();
                                try {
                                    return controller.delete(cod)
                                            .flatMap(deletedFunko -> {
                                                if (isCorrect) {
                                                    logger.info("🟢 Probando caso correcto de Delete...");
                                                } else {
                                                    logger.info("🔴 Probando caso incorrecto de Delete...");
                                                }
                                                logger.info("\nDelete:");
                                                if (deletedFunko != null) {
                                                    String str = "Funko eliminado: " + deletedFunko;
                                                    logger.info(str);
                                                } else {
                                                    logger.info("No se ha eliminado el Funko.");
                                                }
                                                return Mono.empty();
                                            })
                                            .onErrorResume(ex -> {
                                                String strError = "No se ha eliminado el Funko con id " + cod.toString() + " -> " + ex;
                                                logger.error(strError);
                                                return Mono.empty();
                                            });
                                } catch (SQLException e) {
                                    String str = "Error SQL." + e.getMessage();
                                    logger.info(str);
                                } catch (FunkoNotRemovedException e) {
                                    String str = "No se ha eliminado el Funko:" + e.getMessage();
                                    logger.info(str);
                                }
                                return Mono.empty();
                            } else {
                                logger.info("No se ha encontrado el Funko.");
                                return Mono.empty();
                            }
                        })
                        .onErrorResume(e -> {
                            String strError = "No se ha encontrado el Funko con nombre " + name + " -> " + e;
                            logger.error(strError);
                            return Mono.empty();
                        });
            } catch (FunkoNotFoundException e) {
                String str = "No se ha encontrado el Funko:" + e.getMessage();
                logger.info(str);
                return Mono.empty();
            }
        });
    }


    /**
     * Actualiza el nombre de un Funko y lo imprime
     *
     * @param name      Nombre del Funko
     * @param newName   Nuevo nombre del Funko
     * @param isCorrect Si es un caso correcto
     * @return Datos
     */
    private Flux<Object> printUpdate(String name, String newName, boolean isCorrect) {
        return Flux.defer(() -> {
            try {
                return controller.findByName(name)
                        .flatMap(funko -> {
                            if (funko != null) {
                                try {
                                    return controller.update(funko.getCod(),
                                                    Funko.builder()
                                                            .name(newName)
                                                            .model(Model.DISNEY)
                                                            .price(42.42)
                                                            .releaseDate(LocalDate.now())
                                                            .build())
                                            .flatMap(updatedFunko -> {
                                                if (isCorrect) {
                                                    logger.info("🟢 Probando caso correcto de Update...");
                                                } else {
                                                    logger.info("🔴 Probando caso incorrecto de Update...");
                                                }
                                                logger.info("\nUpdate:");
                                                logger.info(updatedFunko.toString());
                                                return Mono.empty();
                                            })
                                            .onErrorResume(ex -> {
                                                String strError = "No se ha actualizado el Funko con nombre " + name + " -> " + ex.getMessage();
                                                logger.error(strError);
                                                return Mono.empty();
                                            });
                                } catch (FunkoNotValidException e) {
                                    String strError = "Funko no válido: " + e;
                                    logger.error(strError);
                                } catch (SQLException e) {
                                    String strError = "Error SQL: " + e;
                                    logger.error(strError);
                                }
                            } else {
                                logger.info("El Funko no se ha encontrado.");
                            }
                            return Mono.empty();
                        })
                        .onErrorResume(e -> {
                            String strError = "No se ha encontrado el Funko con nombre " + name + " -> " + e;
                            logger.error(strError);
                            return Mono.empty();
                        });
            } catch (FunkoNotFoundException e) {
                String strError = "Funko no encontrado: " + e;
                logger.error(strError);
            }
            return Mono.empty();
        });
    }


    /**
     * Realiza una copia de seguridad de la base de datos y la imprime
     *
     * @param rootFolderName Nombre de la carpeta raíz
     * @return Datos
     */
    private Mono<Object> doBackupAndPrint(String rootFolderName) {
        try {
            var backup = controller.exportData(System.getProperty("user.dir") + File.separator + rootFolderName, "backup.json");
            backup.block();
            return backup
                    .then(Mono.fromRunnable(() -> {
                        logger.info("🟢 Copia de seguridad...");
                        logger.info("Copia de seguridad realizada.");
                    }))
                    .onErrorResume(e -> {
                        if (e instanceof SQLException) {
                            String strError = "Fallo SQL: " + e;
                            logger.error(strError);
                        } else if (e instanceof IOException) {
                            String strError = "Error de Input/Output: " + e;
                            logger.error(strError);
                        } else if (e instanceof FunkoNotFoundException) {
                            String strError = "Funko no encontrado: " + e;
                            logger.error(strError);
                        }
                        return Mono.empty();
                    });
        } catch (SQLException e) {
            String str = "Error SQL: " + e;
            logger.error(str);
            return Mono.empty();
        } catch (IOException e) {
            String str = "Error IO: " + e;
            logger.error(str);
            return Mono.empty();
        } catch (FunkoNotFoundException e) {
            String str = "Funko no encontrado: " + e;
            logger.error(str);
            return Mono.empty();
        }
    }


    /**
     * Guarda en la base de datos el Funko pasado por parámetro y lo imprime
     *
     * @param funko     Funko a imprimir
     * @param isCorrect Si es un caso correcto
     * @return Datos
     */
    private Mono<Funko> printSave(Funko funko, boolean isCorrect) {
        return Mono.defer(() -> {
            if (isCorrect) {
                logger.info("🟢 Probando caso correcto de Save...");
            } else {
                logger.info("🔴 Probando caso incorrecto de Save...");
            }

            logger.info("\nSave:");

            try {
                return controller.save(funko)
                        .doOnNext(savedFunko -> {
                            if (savedFunko != null) {
                                logger.info(savedFunko.toString());
                            } else {
                                logger.info("No se ha guardado el Funko.");
                            }
                        })
                        .onErrorResume(e -> {
                            if (e instanceof FunkoNotSavedException || e instanceof FunkoNotValidException) {
                                String strError = "No se ha podido guardar el Funko: " + e.getMessage();
                                logger.error(strError);
                            }
                            return Mono.empty();
                        });
            } catch (SQLException e) {
                String strError = "No se ha podido guardar el Funko: " + e.getMessage();
                logger.error(strError);
            } catch (FunkoNotSavedException e) {
                String strError = "Funko no guardado: " + e.getMessage();
                logger.error(strError);
            } catch (FunkoNotValidException e) {
                String strError = "Funko no válido: " + e.getMessage();
                logger.error(strError);
            }
            return Mono.empty();
        });
    }


    /**
     * Imprime el Funko dado un ID
     *
     * @param id        Id del Funko
     * @param isCorrect Si es un caso correcto
     * @return Datos
     */
    private Mono<Funko> printFindById(UUID id, boolean isCorrect) {
        return Mono.defer(() -> {
            if (isCorrect) {
                logger.info("🟢 Probando caso correcto de FindById...");
            } else {
                logger.info("🔴 Probando caso incorrecto de FindById...");
            }
            logger.info("\nFind by Id:");

            try {
                return controller.findById(id)
                        .doOnNext(funko -> {
                            if (funko != null) {
                                logger.info(funko.toString());
                            } else {
                                logger.info("No se encontró un Funko con el ID especificado.");
                            }
                        })
                        .onErrorResume(e -> {
                            String strError = "No se ha encontrado el Funko con id " + id + " -> " + e.getMessage();
                            logger.error(strError);
                            return Mono.empty();
                        });
            } catch (SQLException | FunkoNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }


    /**
     * Imprime los Funkos que tengan el nombre pasado por parámetro
     *
     * @param name      Nombre de los Funkos
     * @param isCorrect Si es un caso correcto
     * @return Datos
     */
    private Flux<Funko> printFindByName(String name, boolean isCorrect) {
        try {
            return controller.findByName(name)
                    .doOnEach(foundFunkosSignal -> {
                        if (isCorrect) {
                            logger.info("🟢 Probando caso correcto de FindByName...");
                        } else {
                            logger.info("🔴 Probando caso incorrecto de FindByName...");
                        }
                        if (foundFunkosSignal.hasValue()) {
                            logger.info("\nFind by Name:");
                            logger.info(foundFunkosSignal.get().toString());
                        } else if (foundFunkosSignal.hasError()) {
                            Throwable error = foundFunkosSignal.getThrowable();
                            logger.error("Error al buscar Funkos por nombre: " + error.getMessage());
                        }
                    })
                    .onErrorResume(e -> {
                        String str = "Funkos no encontrados: " + e;
                        logger.error(str);
                        return Flux.empty();
                    });
        } catch (FunkoNotFoundException e) {
            String strError = "No se ha encontrado el Funko: " + e.getMessage();
            logger.error(strError);
            return Flux.empty();
        }
    }


    /**
     * Imprime todos los Funkos
     *
     * @return Datos
     */
    private Flux<Funko> printFindAll() {
        logger.info("🟢 Probando caso correcto de FindAll...");
        logger.info("\nFind All:");
        try {
            return controller.findAll()
                    .doOnNext(foundFunko -> logger.info(foundFunko.toString()))
                    .onErrorResume(e -> {
                        String strError = "No se han encontrado Funkos: " + e;
                        logger.error(strError);
                        return Flux.empty();
                    });
        } catch (SQLException | FunkoNotFoundException e) {
            throw new RuntimeException(e);
        }
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
