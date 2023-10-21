package com.madirex.services.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.madirex.models.server.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Servicio para la creación y verificación de tokens
 */
public class TokenService {
    private static TokenService INSTANCE = null;
    private final Logger logger = LoggerFactory.getLogger(TokenService.class);

    /**
     * Constructor de la clase
     */
    private TokenService() {
    }

    /**
     * Devuelve la instancia de la clase
     *
     * @return Instancia de la clase
     */
    public synchronized static TokenService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TokenService();
        }
        return INSTANCE;
    }

    /**
     * Crea un token
     *
     * @param user            Usuario que se quiere guardar en el token
     * @param tokenSecret     Clave secreta del token
     * @param tokenExpiration Tiempo de expiracion del token
     * @return Token creado
     */
    public String createToken(User user, String tokenSecret, long tokenExpiration) {
        logger.debug("Creando token");
        Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
        return JWT.create()
                .withClaim("userid", user.id())
                .withClaim("username", user.username())
                .withClaim("rol", user.role().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + tokenExpiration))
                .sign(algorithm);
    }

    /**
     * Verifica el token
     *
     * @param token       Token a verificar
     * @param tokenSecret Clave secreta del token
     * @param user        Usuario que se quiere verificar
     * @return True si el token es válido, false en caso contrario
     */
    public boolean verifyToken(String token, String tokenSecret, User user) {
        logger.debug("Verificando token");
        Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decodedJWT = verifier.verify(token);
            logger.debug("Token verificado");
            return decodedJWT.getClaim("userid").asLong() == user.id() &&
                    decodedJWT.getClaim("username").asString().equals(user.username()) &&
                    decodedJWT.getClaim("rol").asString().equals(user.role().toString());
        } catch (Exception e) {
            logger.error("Error al verificar el token: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifica el token
     *
     * @param token       Token a verificar
     * @param tokenSecret Clave secreta del token
     * @return True si el token es válido, false en caso contrario
     */
    public boolean verifyToken(String token, String tokenSecret) {
        logger.debug("Verificando token");
        Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token);
            logger.debug("Token verificado");
            return true;
        } catch (Exception e) {
            logger.error("Error al verificar el token: " + e.getMessage());
            return false;
        }
    }

    /**
     * Devuelve los claims del token
     *
     * @param token       Token a verificar
     * @param tokenSecret Clave secreta del token
     * @return Claims del token
     */
    public java.util.Map<String, com.auth0.jwt.interfaces.Claim> getClaims(String token, String tokenSecret) {
        logger.debug("Verificando token");
        Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decodedJWT = verifier.verify(token);
            logger.debug("Token verificado");
            return decodedJWT.getClaims();
        } catch (Exception e) {
            logger.error("Error al verificar el token: " + e.getMessage());
            return null;
        }
    }
}
