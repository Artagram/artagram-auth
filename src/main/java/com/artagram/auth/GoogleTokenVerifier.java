package com.artagram.auth;

import com.artagram.auth.common.ApiException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Valida el ID token que entrega Google Identity Services: firma contra el
 * JWKS de Google (con cache), emisor, audiencia (nuestro client id),
 * vencimiento y que el correo este verificado por Google.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final Set<String> EMISORES = Set.of("https://accounts.google.com", "accounts.google.com");

    private final DefaultJWTProcessor<SecurityContext> processor;

    @Autowired
    public GoogleTokenVerifier(GoogleProperties properties) throws MalformedURLException {
        this(properties.getClientId(),
                JWKSourceBuilder.<SecurityContext>create(URI.create(properties.getJwksUri()).toURL()).build());
    }

    GoogleTokenVerifier(String clientId, JWKSource<SecurityContext> jwkSource) {
        this.processor = new DefaultJWTProcessor<>();
        this.processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        this.processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                clientId,
                new JWTClaimsSet.Builder().build(),
                Set.of("sub", "email", "iss", "exp")));
    }

    public GoogleIdentidad verificar(String idToken) {
        try {
            JWTClaimsSet claims = processor.process(idToken, null);
            if (!EMISORES.contains(claims.getIssuer())) {
                registrarRechazo("EMISOR_INVALIDO", claims.getIssuer());
                throw invalido();
            }
            if (!Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"))) {
                registrarRechazo("CORREO_NO_VERIFICADO", claims.getSubject());
                throw ApiException.forbidden("CORREO_NO_VERIFICADO", "Tu correo de Google no esta verificado");
            }
            return new GoogleIdentidad(claims.getSubject(), claims.getStringClaim("email"),
                    claims.getStringClaim("name"));
        } catch (ApiException e) {
            throw e;
        } catch (ParseException | com.nimbusds.jose.JOSEException
                 | com.nimbusds.jose.proc.BadJOSEException e) {
            registrarRechazo(motivo(e), e.getMessage());
            throw invalido();
        }
    }

    /**
     * Todo intento rechazado queda en el log con su motivo (escenario 5): firma,
     * audiencia, vencimiento, emisor o correo sin verificar. Nunca se registra
     * el token.
     */
    private static void registrarRechazo(String motivo, String detalle) {
        log.warn("Ingreso con Google rechazado: motivo={} detalle={}", motivo, // NOSONAR javasecurity:S5145 se limpian los saltos de línea
                detalle == null ? "" : detalle.replaceAll("[\\r\\n\\t]", "_"));
    }

    static String motivo(Exception e) {
        String mensaje = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (e instanceof ParseException) {
            return "TOKEN_MALFORMADO";
        }
        if (mensaje.contains("expired")) {
            return "TOKEN_VENCIDO";
        }
        if (mensaje.contains("audience")) {
            return "AUDIENCIA_INVALIDA";
        }
        if (mensaje.contains("signature") || mensaje.contains("key")) {
            return "FIRMA_INVALIDA";
        }
        return "TOKEN_INVALIDO";
    }

    private static ApiException invalido() {
        return ApiException.unauthorized("TOKEN_GOOGLE_INVALIDO", "No pudimos validar tu cuenta de Google");
    }
}
