package com.madirex;

import com.madirex.controllers.funko.FunkoControllerImpl;
import com.madirex.exceptions.funko.FunkoNotSavedException;
import com.madirex.exceptions.funko.FunkoNotValidException;
import com.madirex.exceptions.io.ReadCSVFailException;
import com.madirex.models.server.User;
import com.madirex.repositories.funko.FunkoRepositoryImpl;
import com.madirex.repositories.server.UsersRepository;
import com.madirex.services.cache.FunkoCacheImpl;
import com.madirex.services.crud.funko.FunkoServiceImpl;
import com.madirex.services.crud.funko.IdGenerator;
import com.madirex.services.database.DatabaseManager;
import com.madirex.services.io.BackupService;
import com.madirex.services.io.CsvManager;
import com.madirex.server.notifications.FunkoNotificationImpl;
import com.madirex.server.ClientHandler;
import com.madirex.utils.ApplicationProperties;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Clase FunkoProgram que contiene el programa principal
 */
public class FunkoProgram {

    public static final String INSERT_ERROR_MSG = "Error al insertar el Funko en la base de datos: ";
    public static final String KEY_FILE_PROPERTY = "keyFile";
    public static final String KEY_PASSWORD_PROPERTY = "keyPassword";
    private static FunkoProgram funkoProgramInstance;
    private static final AtomicLong clientNumber = new AtomicLong(0);
    private static final Logger logger = LoggerFactory.getLogger(FunkoProgram.class);
    private static final FunkoControllerImpl controller = FunkoControllerImpl.getInstance(FunkoServiceImpl
            .getInstance(FunkoRepositoryImpl.getInstance(IdGenerator.getInstance(), DatabaseManager.getInstance()),
                    new FunkoCacheImpl(15, 90),
                    new BackupService<>(), FunkoNotificationImpl.getInstance()));

    /**
     * Constructor privado para evitar la creación de instancia
     * SINGLETON
     */
    private FunkoProgram() {
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
        Disposable notificationSystem = loadNotificationSystem();
        addDefaultUsers();
        var loadAndUploadFunkos = loadFunkosFileAndInsertToDatabase("data" + File.separator + "funkos.csv");
        loadAndUploadFunkos.block();
        logger.info("Funkos cargados en la base de datos.");
        initServer(notificationSystem);
    }

    /**
     * Añade usuarios por defecto
     */
    private void addDefaultUsers() {
        UsersRepository userRepo = UsersRepository.getInstance();
        userRepo.addUser(User.builder()
                .id(UUID.randomUUID().toString())
                .username("Madi")
                .password(BCrypt.hashpw("madi1234", BCrypt.gensalt(12)))
                .role(User.Role.ADMIN)
                .build());
        userRepo.addUser(User.builder()
                .id(UUID.randomUUID().toString())
                .username("Marv")
                .password(BCrypt.hashpw("marv1234", BCrypt.gensalt(12)))
                .role(User.Role.USER)
                .build());
    }

    /**
     * Inicia el servidor
     *
     * @param notificationSystem Disposable del sistema de notificaciones
     */
    private void initServer(Disposable notificationSystem) {
        var myConfig = readConfigFile();
        logger.debug("Configurando TSL");
        System.setProperty("javax.net.ssl.keyStore", myConfig.get(KEY_FILE_PROPERTY));
        System.setProperty("javax.net.ssl.keyStorePassword", myConfig.get(KEY_PASSWORD_PROPERTY));
        SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        try (SSLServerSocket serverSocket = (SSLServerSocket) serverFactory
                .createServerSocket(Integer.parseInt(myConfig.get("port")))) {
            String str = "Protocolos soportados: " + Arrays.toString(serverSocket.getSupportedProtocols());
            logger.debug(str);
            serverSocket.setEnabledCipherSuites(new String[]{"TLS_AES_128_GCM_SHA256"});
            serverSocket.setEnabledProtocols(new String[]{"TLSv1.3"});
            logger.debug("🚀 Servidor escuchando en el puerto 3000");
            AtomicBoolean exit = new AtomicBoolean(false);
            Flux<String> userInputFlux = Mono.create((MonoSink<String> sink) -> {
                Scanner scanner = new Scanner(System.in);
                logger.info("Escribe exit para salir.");
                while (!exit.get()) {
                    String userInput = scanner.nextLine();
                    if (userInput.equalsIgnoreCase("exit")) {
                        exit.set(true);
                        notificationSystem.dispose();
                        logger.debug("Cerrando servidor...");
                    }
                    sink.success(userInput);
                }
                scanner.close();
            }).flux();
            Flux<ClientHandler> clientHandlerFlux = Flux.generate(sink -> Mono.fromCallable(() -> new ClientHandler(serverSocket.accept(),
                            clientNumber.incrementAndGet(), controller, myConfig))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            sink::next,
                            sink::error
                    ));

            clientHandlerFlux.takeUntilOther(userInputFlux).subscribe(ClientHandler::start);
        } catch (IOException e) {
            String msg = "Error: " + e.getMessage();
            logger.error(msg);
        } finally {
            controller.shutdown();
            logger.info("Programa de Funkos finalizado.");
        }
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
                    String msg = switch (notification.getType()) {
                        case NEW -> "🟢 Funko insertado: " + notification.getContent();
                        case UPDATED -> "🟠 Funko actualizado: " + notification.getContent();
                        case DELETED -> "🔴 Funko eliminado: " + notification.getContent();
                    };
                    logger.info(msg);
                },
                error -> {
                    String str = "Se ha producido un error: " + error;
                    logger.error(str);
                },
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
                                        String str = INSERT_ERROR_MSG + e.getMessage();
                                        logger.error(str);
                                        return Mono.empty();
                                    });
                        } catch (SQLException | FunkoNotSavedException e) {
                            String stre = INSERT_ERROR_MSG + e.getMessage();
                            logger.error(stre);
                        } catch (FunkoNotValidException e) {
                            String stre = "Error al insertar el Funko en la base de datos (Funko no válido): " + e.getMessage();
                            logger.error(stre);
                        }
                        return Mono.empty();
                    })
                    .then();
        } catch (ReadCSVFailException e) {
            String str = "Error al leer el fichero CSV: " + e.getMessage();
            logger.error(str);
            return Mono.empty();
        }
    }

    /**
     * Lee el fichero de propiedades del servidor
     *
     * @return Mapa con las propiedades del servidor
     */
    public static Map<String, String> readConfigFile() {
        try {
            logger.debug("Leyendo el fichero de propiedades");
            ApplicationProperties properties = ApplicationProperties.getInstance();

            String keyFile = properties.readProperty(ApplicationProperties.PropertyType.SERVER,
                    KEY_FILE_PROPERTY, "./cert/server_keystore.p12");
            String keyPassword = properties.readProperty(ApplicationProperties.PropertyType.SERVER,
                    KEY_PASSWORD_PROPERTY, "password");
            String tokenSecret = properties.readProperty(ApplicationProperties.PropertyType.SERVER,
                    "tokenSecret", "ñfj08354hgoiepmñlhñeoñe5jop.-45yh4");
            String tokenExpiration = properties.readProperty(ApplicationProperties.PropertyType.SERVER,
                    "tokenExpiration", "10000");
            String port = properties.readProperty(ApplicationProperties.PropertyType.SERVER,
                    "port", "3000");

            if (keyFile.isEmpty() || keyPassword.isEmpty()) {
                throw new IllegalStateException("Error al procesar el fichero de propiedades del servidor.");
            }

            if (!Files.exists(Path.of(keyFile))) {
                throw new FileNotFoundException("No se encuentra el fichero de la clave.");
            }

            Map<String, String> configMap = new HashMap<>();
            configMap.put(KEY_FILE_PROPERTY, keyFile);
            configMap.put(KEY_PASSWORD_PROPERTY, keyPassword);
            configMap.put("tokenSecret", tokenSecret);
            configMap.put("tokenExpiration", tokenExpiration);
            configMap.put("port", port);
            return configMap;
        } catch (FileNotFoundException e) {
            String str = "Error en clave: " + e.getLocalizedMessage();
            logger.error(str);
            System.exit(1);
            return Collections.emptyMap();
        }
    }
}
