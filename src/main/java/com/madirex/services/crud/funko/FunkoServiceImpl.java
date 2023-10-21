package com.madirex.services.crud.funko;

import com.madirex.exceptions.FunkoNotFoundException;
import com.madirex.exceptions.FunkoNotRemovedException;
import com.madirex.models.Notification;
import com.madirex.models.funko.Funko;
import com.madirex.models.funko.Model;
import com.madirex.repositories.funko.FunkoRepositoryImpl;
import com.madirex.services.cache.FunkoCache;
import com.madirex.services.io.BackupService;
import com.madirex.services.notifications.FunkoNotificationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Implementación de la interfaz FunkoService
 */
public class FunkoServiceImpl implements FunkoService<List<Funko>> {
    public static final String FUNKO_WITH_ID_MSG = "Funko con ID ";
    private static FunkoServiceImpl funkoServiceImplInstance;

    private final FunkoCache cache;
    private final Logger logger = LoggerFactory.getLogger(FunkoServiceImpl.class);
    private final FunkoRepositoryImpl funkoRepository;
    private final BackupService<List<Funko>> backupService;
    private final FunkoNotificationImpl funkoNotification;

    /**
     * Constructor de la clase
     *
     * @param funkoRepository   Instancia de la clase FunkoRepository
     * @param cache             Instancia de la clase FunkoCache
     * @param backupService     Instancia de la clase BackupService
     * @param funkoNotification Instancia de la clase FunkoNotification
     */
    private FunkoServiceImpl(FunkoRepositoryImpl funkoRepository, FunkoCache cache,
                             BackupService<List<Funko>> backupService, FunkoNotificationImpl funkoNotification) {
        this.funkoRepository = funkoRepository;
        this.cache = cache;
        this.backupService = backupService;
        this.funkoNotification = funkoNotification;
    }

    /**
     * Devuelve la instancia de la clase
     *
     * @param funkoRepository   Instancia de la clase FunkoRepository
     * @param cache             Instancia de la clase FunkoCache
     * @param backupService     Instancia de la clase BackupService
     * @param funkoNotification Instancia de la clase FunkoNotification
     * @return Instancia de la clase
     */
    public static synchronized FunkoServiceImpl getInstance(FunkoRepositoryImpl funkoRepository,
                                                            FunkoCache cache,
                                                            BackupService<List<Funko>> backupService,
                                                            FunkoNotificationImpl funkoNotification) {
        if (funkoServiceImplInstance == null) {
            funkoServiceImplInstance = new FunkoServiceImpl(funkoRepository, cache, backupService, funkoNotification);
        }
        return funkoServiceImplInstance;
    }


    /**
     * Devuelve todos los elementos del repositorio
     *
     * @return Lista de elementos
     */
    @Override
    public Flux<Funko> findAll() {
        logger.debug("Obteniendo todos los Funkos");
        return funkoRepository.findAll();
    }

    /**
     * Busca elementos en el repositorio por su modelo
     *
     * @param model Modelo de los elementos a buscar
     * @return Lista de elementos encontrados
     */
    @Override
    public Flux<Funko> findByModel(Model model) {
        logger.debug("Obteniendo todos los Funkos por su nombre");
        return funkoRepository.findByModel(model)
                .collectList()
                .flatMapMany(list -> {
                    if (list.isEmpty()) {
                        return Flux.error(new FunkoNotFoundException("No se encontraron Funkos con el modelo: " + model));
                    } else {
                        return Flux.fromIterable(list);
                    }
                });
    }

    /**
     * Busca elementos en el repositorio por año de lanzamiento
     *
     * @param year Año de lanzamiento de los elementos a buscar
     * @return Lista de elementos encontrados
     */
    @Override
    public Flux<Funko> findByReleaseYear(Integer year) {
        logger.debug("Obteniendo todos los Funkos por año de lanzamiento");
        return funkoRepository.findByReleaseYear(year)
                .collectList()
                .flatMapMany(list -> {
                    if (list.isEmpty()) {
                        return Flux.error(new
                                FunkoNotFoundException("No se encontraron Funkos lanzados en el año: " + year));
                    } else {
                        return Flux.fromIterable(list);
                    }
                });
    }

    /**
     * Realiza un backup de los datos del repositorio
     *
     * @param path     Ruta del directorio donde se guardará el backup
     * @param fileName Nombre del archivo del backup
     * @param data     Datos a guardar
     * @throws SQLException Si hay un error en la base de datos
     */
    @Override
    public Mono<Void> exportData(String path, String fileName, List<Funko> data) throws SQLException {
        return findAll().collectList().flatMap(s -> backupService.exportData(path, fileName, s));
    }

    /**
     * Importa los datos de un archivo JSON
     *
     * @param path     Ruta del directorio donde se guardará el backup
     * @param fileName Nombre del archivo del backup
     * @return Datos importados
     */
    @Override
    public Flux<Funko> importData(String path, String fileName) {
        return backupService.importData(path, fileName);
    }

    /**
     * Devuelve un elemento del repositorio
     *
     * @param id Id del elemento a buscar
     * @return Elemento encontrado
     */
    @Override
    public Mono<Funko> findById(UUID id) {
        return cache.get(id.toString())
                .switchIfEmpty(funkoRepository.findById(id)
                        .flatMap(funko -> cache.put(id.toString(), funko)
                                .then(Mono.just(funko)))
                        .switchIfEmpty(Mono.error(new FunkoNotFoundException(FUNKO_WITH_ID_MSG + id + " no encontrado."))));
    }

    /**
     * Guarda un elemento en el repositorio
     *
     * @param funko Elemento a guardar
     * @return Elemento guardado
     */
    @Override
    public Mono<Funko> save(Funko funko) {
        logger.debug("Guardando Funko");
        return funkoRepository.save(funko)
                .doOnSuccess(saved -> funkoNotification.notify(new Notification<>(Notification.Type.NEW, saved)));
    }

    /**
     * Actualiza un elemento del repositorio
     *
     * @param funkoId  Id del elemento a actualizar
     * @param newFunko Elemento con los nuevos datos
     * @return Elemento actualizado
     */
    @Override
    public Mono<Funko> update(UUID funkoId, Funko newFunko) {
        logger.debug("Actualizando Funko");
        return funkoRepository.findById(funkoId)
                .switchIfEmpty(Mono.error(new FunkoNotFoundException(FUNKO_WITH_ID_MSG + funkoId + " no encontrado")))
                .flatMap(existing -> funkoRepository.update(funkoId, newFunko)
                        .flatMap(updated -> cache.put(String.valueOf(funkoId), updated)
                                .thenReturn(updated)))
                .doOnSuccess(saved -> funkoNotification.notify(new Notification<>(Notification.Type.UPDATED, saved)));
    }

    /**
     * Borra un elemento del repositorio
     *
     * @param id Id del elemento a borrar
     * @return ¿Borrado?
     */
    @Override
    public Mono<Funko> delete(UUID id) {
        logger.debug("Eliminando Funko");
        return funkoRepository.findById(id)
                .switchIfEmpty(Mono.error(new FunkoNotFoundException(FUNKO_WITH_ID_MSG + id + " no encontrado")))
                .flatMap(funko -> cache.remove(funko.getCod().toString())
                        .then(funkoRepository.delete(funko.getCod()))
                        .thenReturn(funko))
                .onErrorResume(ex -> Mono.error(new FunkoNotRemovedException(FUNKO_WITH_ID_MSG + id + " no eiminado")))
                .doOnSuccess(saved -> funkoNotification.notify(new Notification<>(Notification.Type.DELETED, saved)));
    }

    /**
     * Cierra el caché
     */
    public void shutdown() {
        cache.shutdown();
    }

    /**
     * Recibe las notificaciones
     *
     * @return notificaciones
     */
    public Flux<Notification<Funko>> getNotifications() {
        return funkoNotification.getNotificationAsFlux();
    }
}