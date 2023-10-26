package com.madirex.services.database;

import com.madirex.utils.ApplicationProperties;
import io.github.cdimascio.dotenv.Dotenv;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.stream.Collectors;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * Controlador de Bases de Datos
 */
public class DatabaseManager {
    private static DatabaseManager controller;
    private final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private String databaseName;
    private String user;
    private String password;
    private String initScript;
    private String connectionUrl;
    private boolean dataInitialized = false;
    private final ConnectionFactory connectionFactory;

    @Getter
    private final ConnectionPool pool;

    /**
     * Devuelve una instancia del controlador
     *
     * @return instancia del controladorBD
     */
    public static synchronized DatabaseManager getInstance() {
        if (controller == null) {
            controller = new DatabaseManager();
        }
        return controller;
    }

    /**
     * Constructor privado para Singleton
     */
    private DatabaseManager() {
        initConfig();
        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, ApplicationProperties.getInstance()
                        .readProperty(ApplicationProperties.PropertyType.DATABASE,
                                "db.driver", "h2"))
                .option(PROTOCOL, ApplicationProperties.getInstance()
                        .readProperty(ApplicationProperties.PropertyType.DATABASE,
                                "db.protocol", "file"))
                .option(USER, user)
                .option(PASSWORD, password)
                .option(DATABASE, connectionUrl + databaseName)
                .build();

        connectionFactory = ConnectionFactories.get(options);

        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration
                .builder(connectionFactory)
                .maxIdleTime(Duration.ofMillis(1000))
                .maxSize(20)
                .build();


        if (initScript.equalsIgnoreCase("true")) {
            initData();
        }

        pool = new ConnectionPool(configuration);
    }

    /**
     * Carga la configuración de acceso al servidor de Base de Datos
     */
    private synchronized void initConfig() {
        ApplicationProperties properties = ApplicationProperties.getInstance();
        databaseName = properties.readProperty(ApplicationProperties.PropertyType.DATABASE,
                "db.name", "AppDatabase");
        initScript = properties.readProperty(ApplicationProperties.PropertyType.DATABASE,
                "db.init", "false");
        Dotenv dotenv = Dotenv.load();
        user = dotenv.get("DATABASE_USER");
        password = dotenv.get("DATABASE_PASSWORD");
        connectionUrl = System.getProperty("user.home")
                .replace("\\", "/") + "/" + this.databaseName;
        logger.debug("Configuración de acceso a la Base de Datos cargada");
    }

    /**
     * Ejecuta script de inicialización de la base de datos
     *
     * @param scriptSqlFile Fichero con el script de inicialización
     * @return Mono<Void> Resultado de la ejecución
     */
    public Mono<Void> executeInitScript(String scriptSqlFile) {
        String str = "Ejecutando script de inicialización de la base de datos: " + scriptSqlFile;
        logger.debug(str);
        return Mono.usingWhen(
                connectionFactory.create(),
                connection -> {
                    logger.debug("Creando conexión con la base de datos");
                    return getMonoExecuteInitScript(scriptSqlFile, connection);
                },
                Connection::close
        ).then();
    }

    /**
     * Ejecuta el script de inicialización de la base de datos
     *
     * @param scriptSqlFile Fichero con el script de inicialización
     * @param connection    Conexión con la base de datos
     * @return Mono<Void> Resultado de la ejecución
     */
    @NotNull
    private Mono<? extends Result> getMonoExecuteInitScript(String scriptSqlFile, Connection connection) {
        String scriptContent;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(scriptSqlFile)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                scriptContent = reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            logger.error("Error al leer el script de inicialización de la base de datos");
            return Mono.error(e);
        }
        Statement statement = connection.createStatement(scriptContent);
        return Mono.from(statement.execute());
    }

    /**
     * Inicializa la base de datos con los datos del fichero init.sql y remove.sql
     * Solo si el properties tiene la propiedad db.init en TRUE
     */
    public synchronized void initData() {
        if (!dataInitialized && initScript.equalsIgnoreCase("true")) {
            logger.debug("Borrando tablas de la base de datos.");
            executeInitScript("sql/remove.sql").block();
            logger.debug("Inicializando tablas de la base de datos.");
            executeInitScript("sql/init.sql").block();
            logger.debug("Tabla de la base de datos inicializada.");
            dataInitialized = true;
        }
    }

}