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
import com.madirex.services.notifications.FunkoNotificationImpl;
import com.madirex.services.server.ClientHandler;
import com.madirex.utils.ApplicationProperties;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Clase FunkoProgram que contiene el programa principal
 */
public class FunkoProgram {

    private static FunkoProgram funkoProgramInstance;
    private static final AtomicLong clientNumber = new AtomicLong(0);
    private static final Logger logger = LoggerFactory.getLogger(FunkoProgram.class);
    private static final FunkoControllerImpl controller = FunkoControllerImpl.getInstance(FunkoServiceImpl
            .getInstance(FunkoRepositoryImpl.getInstance(IdGenerator.getInstance(), DatabaseManager.getInstance()),
                    new FunkoCacheImpl(15, 90),
                    new BackupService(), FunkoNotificationImpl.getInstance()));

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
        loadNotificationSystem();
        addDefaultUsers();
        var loadAndUploadFunkos = loadFunkosFileAndInsertToDatabase("data" + File.separator + "funkos.csv");
        loadAndUploadFunkos.block();
        logger.info("Funkos cargados en la base de datos.");
        initServer();
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
     */
    private void initServer() {
        try {
            var myConfig = readConfigFile();
            logger.debug("Configurando TSL");
            System.setProperty("javax.net.ssl.keyStore", myConfig.get("keyFile")); // Llavero
            System.setProperty("javax.net.ssl.keyStorePassword", myConfig.get("keyPassword")); // Clave de acceso
            SSLServerSocketFactory serverFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket = (SSLServerSocket) serverFactory.createServerSocket(Integer.parseInt(myConfig.get("port")));
            String str = "Protocolos soportados: " + Arrays.toString(serverSocket.getSupportedProtocols());
            logger.debug(str);
            serverSocket.setEnabledCipherSuites(new String[]{"TLS_AES_128_GCM_SHA256"});
            serverSocket.setEnabledProtocols(new String[]{"TLSv1.3"});
            logger.debug("🚀 Servidor escuchando en el puerto 3000");
            while (true) {
                new ClientHandler(serverSocket.accept(), clientNumber.incrementAndGet(), controller, myConfig).start();
            }
        } catch (IOException e) {
            String msg = "Error: " + e.getMessage();
            logger.error(msg);
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
                    "keyFile", "./cert/server_keystore.p12");
            String keyPassword = properties.readProperty(ApplicationProperties.PropertyType.SERVER,
                    "keyPassword", "password");
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
            configMap.put("keyFile", keyFile);
            configMap.put("keyPassword", keyPassword);
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
