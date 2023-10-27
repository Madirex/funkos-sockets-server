package com.madirex.server.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.madirex.models.server.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;

/**
 * Servicio para la creación y verificación de tokens
 */
public class TokenService {
    public static final String TOKEN_VERIFICATION_ERROR_MSG = "Error al verificar el token: ";
    public static final String TOKEN_VERIFICATION_MSG = "Token verificado";
    public static final String TOKEN_VERIFICATION_PROCESS_MSG = "Verificando token";
    private static TokenService instance = null;
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
    public static synchronized TokenService getInstance() {
        if (instance == null) {
            instance = new TokenService();
        }
        return instance;
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
     * @return True si el token es válido, false en caso contrario
     */
    public boolean verifyToken(String token, String tokenSecret) {
        logger.debug(TOKEN_VERIFICATION_PROCESS_MSG);
        Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token);
            logger.debug(TOKEN_VERIFICATION_MSG);
            return true;
        } catch (Exception e) {
            String stre = TOKEN_VERIFICATION_ERROR_MSG + e.getMessage();
            logger.error(stre);
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
        logger.debug(TOKEN_VERIFICATION_PROCESS_MSG);
        Algorithm algorithm = Algorithm.HMAC256(tokenSecret);
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decodedJWT = verifier.verify(token);
            logger.debug(TOKEN_VERIFICATION_MSG);
            return decodedJWT.getClaims();
        } catch (Exception e) {
            String stre = TOKEN_VERIFICATION_ERROR_MSG + e.getMessage();
            logger.error(stre);
            return Collections.emptyMap();
        }
    }
}
