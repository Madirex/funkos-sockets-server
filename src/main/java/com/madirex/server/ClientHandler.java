package com.madirex.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.madirex.controllers.funko.FunkoControllerImpl;
import com.madirex.exceptions.funko.FunkoNotSavedException;
import com.madirex.exceptions.funko.FunkoNotValidException;
import com.madirex.exceptions.server.ServerException;
import com.madirex.models.funko.Funko;
import com.madirex.models.funko.Model;
import com.madirex.models.server.Login;
import com.madirex.models.server.Request;
import com.madirex.models.server.Response;
import com.madirex.models.server.User;
import com.madirex.repositories.server.UsersRepository;
import com.madirex.server.jwt.TokenService;
import com.madirex.utils.LocalDateAdapter;
import com.madirex.utils.LocalDateTimeAdapter;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Clase ClientHandler que se encarga de recibir, manejar y responder a los datos enviados por el cliente
 */
public class ClientHandler extends Thread {
    public static final String AUTH_FAIL_MSG = "Usuario no autenticado correctamente o no tiene permisos para esta acción";
    public static final String TOKEN_SECRET_CONFIG_NAME = "tokenSecret";
    public static final String SENT_RESPONSE_MSG = "Respuesta enviada: ";
    private final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Socket clientSocket;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    private final long clientNumber;
    private final FunkoControllerImpl funkoController;
    private BufferedReader in;
    private PrintWriter out;
    private final Map<String, String> config;


    /**
     * Constructor
     *
     * @param socket          socket del cliente
     * @param clientNumber    número del cliente
     * @param funkoController controlador de Funko
     * @param config          configuración
     */
    public ClientHandler(Socket socket, long clientNumber, FunkoControllerImpl funkoController, Map<String, String> config) {
        this.clientSocket = socket;
        this.clientNumber = clientNumber;
        this.funkoController = funkoController;
        this.config = config;
    }

    /**
     * Método run que se encarga de recibir, manejar y responder a los datos enviados por el cliente
     */
    @Override
    public void run() {
        try {
            openConnection();
            String clientInput;
            Request request;
            while (true) {
                clientInput = in.readLine();
                String str = "Petición recibida en bruto: " + clientInput;
                if (clientInput == null || clientInput.equals("exit")) {
                    break;
                }
                logger.debug(str);
                request = gson.fromJson(clientInput, Request.class);
                handleRequest(request);
            }
        } catch (IOException e) {
            String msg = "Error: " + e.getMessage();
            logger.error(msg);
        } catch (ServerException ex) {
            out.println(gson.toJson(new Response(Response.Status.ERROR, ex.getMessage(), LocalDateTime.now().toString())));
        }
    }

    /**
     * Cierra la conexión con el cliente
     *
     * @throws IOException excepción de entrada/salida
     */
    private void closeConnection() throws IOException {
        String str = "Cerrando la conexión con el cliente: " + clientSocket
                .getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        logger.debug(str);
        out.close();
        in.close();
        clientSocket.close();
    }

    /**
     * Abre la conexión con el cliente
     *
     * @throws IOException excepción de entrada/salida
     */
    private void openConnection() throws IOException {
        String str = "Conectando con el cliente nº: " + clientNumber
                + " : " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        logger.debug(str);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new PrintWriter(clientSocket.getOutputStream(), true);
    }

    /**
     * Maneja la petición del cliente
     *
     * @param request petición del cliente
     * @throws IOException     excepción de entrada/salida
     * @throws ServerException excepción del servidor
     */
    private void handleRequest(Request request) throws IOException, ServerException {
        String str = "Procesando petición " + request;
        logger.debug(str);
        switch (request.type()) {
            case LOGIN -> processLogin(request);
            case EXIT -> processExit();
            case GETALL -> processGetAll(request);
            case GETBYID -> processGetById(request);
            case GETBYMODEL -> processGetByModel(request);
            case GETBYRELEASEYEAR -> processGetByReleaseYear(request);
            case INSERT -> processInsert(request);
            case UPDATE -> processUpdate(request);
            case DELETE -> processDelete(request);
            default -> new Response(Response.Status.ERROR, "Tipo de consulta desconocida.",
                    LocalDateTime.now().toString());
        }
    }

    /**
     * Procesa la petición de login
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processLogin(Request request) throws ServerException {
        String str = "Petición de login recibida: " + request;
        logger.debug(str);
        Login login = gson.fromJson(String.valueOf(request.content()), new TypeToken<Login>() {
        }.getType());
        var user = UsersRepository.getInstance().findByUsername(login.username());
        if (user.isEmpty() || !BCrypt.checkpw(login.password(), user.get().password())) {
            logger.warn("El usuario no existe o la contraseña es incorrecta");
            throw new ServerException("Usuario o contraseña incorrectos");
        }
        var token = TokenService.getInstance().createToken(user.get(), config.get(TOKEN_SECRET_CONFIG_NAME),
                Long.parseLong(config.get("tokenExpiration")));
        String msg = SENT_RESPONSE_MSG + token;
        logger.debug(msg);
        out.println(gson.toJson(new Response(Response.Status.TOKEN, token, LocalDateTime.now().toString())));
    }

    /**
     * Procesa la petición de salida
     *
     * @throws IOException excepción de entrada/salida
     */
    private void processExit() throws IOException {
        out.println(gson.toJson(new Response(Response.Status.BYE, "Cerrando conexión", LocalDateTime.now().toString())));
        closeConnection();
    }


    /**
     * Procesa el Token
     *
     * @param token token
     * @return Optional<User>
     * @throws ServerException excepción del servidor
     */
    private Optional<User> processToken(String token) throws ServerException {
        if (TokenService.getInstance().verifyToken(token, config.get(TOKEN_SECRET_CONFIG_NAME))) {
            logger.debug("Token válido");
            var claims = TokenService.getInstance().getClaims(token, config.get(TOKEN_SECRET_CONFIG_NAME));
            var id = String.valueOf(claims.get("userid"));
            var user = UsersRepository.getInstance().findById(id);
            if (user.isEmpty()) {
                logger.error("Usuario no autenticado correctamente.");
                throw new ServerException("Usuario no autenticado correctamente.");
            }
            return user;
        } else {
            logger.error("Token no válido.");
            throw new ServerException("Token no válido.");
        }
    }

    /**
     * Procesa la petición GetAll
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processGetAll(Request request) throws ServerException {
        processToken(request.token());
        funkoController.findAll()
                .collectList()
                .subscribe(funkos -> {
                    String msg = SENT_RESPONSE_MSG + funkos;
                    logger.debug(msg);
                    var resJson = gson.toJson(funkos);
                    out.println(gson.toJson(new Response(Response.Status.OK, resJson, LocalDateTime.now().toString())));
                });
    }

    /**
     * Procesa la petición GetById
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processGetById(Request request) throws ServerException {
        processToken(request.token());
        funkoController.findById(UUID.fromString(request.content())).subscribe(
                funko -> {
                    String str = SENT_RESPONSE_MSG + funko;
                    logger.debug(str);
                    var resJson = gson.toJson(funko);
                    out.println(gson.toJson(new Response(Response.Status.OK, resJson, LocalDateTime.now().toString())));
                },
                error -> {
                    String stre = "Funko con ID " + request.content() + "no encontrado.";
                    logger.warn(stre);
                    out.println(gson.toJson(new Response(Response.Status.ERROR, error.getMessage(),
                            LocalDateTime.now().toString())));
                }
        );
    }

    /**
     * Procesa la petición GetByModel
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processGetByModel(Request request) throws ServerException {
        processToken(request.token());
        var model = Model.valueOf(request.content());
        funkoController.findByModel(model).subscribe(
                funko -> {
                    String str = SENT_RESPONSE_MSG + funko;
                    logger.debug(str);
                    var resJson = gson.toJson(funko);
                    out.println(gson.toJson(new Response(Response.Status.OK, resJson, LocalDateTime.now().toString())));
                },
                error -> {
                    String stre = "No se ha encontrado ningún Funko con el modelo: " + request.content();
                    logger.error(stre);
                    out.println(gson.toJson(new Response(Response.Status.ERROR, error.getMessage(),
                            LocalDateTime.now().toString())));
                }
        );

    }

    /**
     * Procesa la petición GetByReleaseYear
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processGetByReleaseYear(Request request) throws ServerException {
        processToken(request.token());
        var year = Integer.valueOf(request.content());
        funkoController.findByReleaseYear(year).subscribe(
                funko -> {
                    String str = SENT_RESPONSE_MSG + funko;
                    logger.debug(str);
                    var resJson = gson.toJson(funko);
                    out.println(gson.toJson(new Response(Response.Status.OK, resJson, LocalDateTime.now().toString())));
                },
                error -> {
                    String stre = "No se ha encontrado ningún Funko con año de lanzamiento: " + request.content();
                    logger.error(stre);
                    out.println(gson.toJson(new Response(Response.Status.ERROR, error.getMessage(),
                            LocalDateTime.now().toString())));
                }
        );

    }

    /**
     * Procesa la petición Insert
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processInsert(Request request) throws ServerException {
        var user = processToken(request.token());
        AtomicBoolean hasPermission = new AtomicBoolean(false);
        user.ifPresent(value -> hasPermission.set(user.get().role().equals(User.Role.ADMIN) ||
                user.get().role().equals(User.Role.USER)));
        if (hasPermission.get()) {
            Funko funkoToSave = gson.fromJson(String.valueOf(request.content()), new TypeToken<Funko>() {
            }.getType());
            try {
                funkoController.save(funkoToSave).subscribe(
                        funko -> {
                            String str = SENT_RESPONSE_MSG + funko;
                            logger.debug(str);
                            var resJson = gson.toJson(funko);
                            out.println(gson.toJson(new Response(Response.Status.OK, resJson,
                                    LocalDateTime.now().toString())));
                        },
                        error -> {
                            String stre = "Funko no encontrado con ID: " + error.getMessage();
                            logger.error(stre);
                            out.println(gson.toJson(new Response(Response.Status.ERROR, error.getMessage(),
                                    LocalDateTime.now().toString())));
                        }
                );

            } catch (SQLException e) {
                String str = "Error al insertar el Funko en la base de datos: " + e.getMessage();
                logger.error(str);
                throw new ServerException(str);
            } catch (FunkoNotSavedException e) {
                String str = "Error al guardar el Funko en la base de datos: " + e.getMessage();
                logger.error(str);
                throw new ServerException(str);
            } catch (FunkoNotValidException e) {
                String str = "Funko no válido. No se ha agregado a la base de datos: " + e.getMessage();
                logger.error(str);
                throw new ServerException(str);
            }
        } else {
            logger.error(AUTH_FAIL_MSG);
            throw new ServerException(AUTH_FAIL_MSG);
        }
    }

    /**
     * Procesa la petición Update
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processUpdate(Request request) throws ServerException {
        var user = processToken(request.token());
        AtomicBoolean hasPermission = new AtomicBoolean(false);
        user.ifPresent(value -> hasPermission.set(user.get().role().equals(User.Role.ADMIN) ||
                user.get().role().equals(User.Role.USER)));
        if (hasPermission.get()) {
            Funko funkoToUpdate = gson.fromJson(String.valueOf(request.content()), new TypeToken<Funko>() {
            }.getType());
            try {
                funkoController.update(funkoToUpdate.getCod(), funkoToUpdate).subscribe(
                        funko -> {
                            String str = SENT_RESPONSE_MSG + funko;
                            logger.debug(str);
                            var resJson = gson.toJson(funko);
                            out.println(gson.toJson(new Response(Response.Status.OK, resJson,
                                    LocalDateTime.now().toString())));
                        },
                        error -> {
                            String stre = "Funko no encontrado con id: " + error.getMessage();
                            logger.error(stre);
                            out.println(gson.toJson(new Response(Response.Status.ERROR, error.getMessage(),
                                    LocalDateTime.now().toString())));
                        }
                );
            } catch (FunkoNotValidException e) {
                String str = "Funko no válido. No se ha agregado a la base de datos: " + e.getMessage();
                logger.error(str);
                throw new ServerException(str);
            }
        } else {
            logger.error(AUTH_FAIL_MSG);
            throw new ServerException(AUTH_FAIL_MSG);
        }
    }

    /**
     * Procesa la petición Delete
     *
     * @param request petición del cliente
     * @throws ServerException excepción del servidor
     */
    private void processDelete(Request request) throws ServerException {
        var user = processToken(request.token());
        AtomicBoolean hasPermission = new AtomicBoolean(false);
        user.ifPresent(value -> hasPermission.set(user.get().role().equals(User.Role.ADMIN)));
        if (hasPermission.get()) {
            funkoController.delete(UUID.fromString(request.content())).subscribe(
                    funko -> {
                        var resJson = gson.toJson(funko);
                        out.println(gson.toJson(new Response(Response.Status.OK, resJson,
                                LocalDateTime.now().toString())));
                    },
                    error -> {
                        String stre = "Funko no encontrado con ID: " + request.content();
                        logger.error(stre);
                        out.println(gson.toJson(new Response(Response.Status.ERROR, error.getMessage(),
                                LocalDateTime.now().toString())));
                    }
            );
        } else {
            logger.error(AUTH_FAIL_MSG);
            throw new ServerException(AUTH_FAIL_MSG);
        }
    }
}