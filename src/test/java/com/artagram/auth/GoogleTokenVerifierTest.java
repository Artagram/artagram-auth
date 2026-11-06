package com.artagram.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.artagram.auth.common.ApiException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "artagram.apps.googleusercontent.com";

    private static RSAKey llave;
    private static RSAKey llaveAjena;
    private static GoogleTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        llave = new RSAKeyGenerator(2048).keyID("google-1").generate();
        llaveAjena = new RSAKeyGenerator(2048).keyID("google-1").generate();
        verifier = new GoogleTokenVerifier(CLIENT_ID, new ImmutableJWKSet<>(new JWKSet(llave.toPublicJWK())));
    }

    private static JWTClaimsSet.Builder claimsValidos() {
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-123")
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", "lucia@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Lucía");
    }

    private static String firmar(JWTClaimsSet claims, RSAKey con) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(con.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(new RSASSASigner(con));
        return jwt.serialize();
    }

    @Test
    void tokenValidoDevuelveLaIdentidad() throws Exception {
        GoogleIdentidad identidad = verifier.verificar(firmar(claimsValidos().build(), llave));

        assertThat(identidad).isEqualTo(new GoogleIdentidad("google-123", "lucia@gmail.com", "Lucía"));
    }

    @Test
    void firmaDeOtraLlaveSeRechaza() throws Exception {
        String token = firmar(claimsValidos().build(), llaveAjena);

        assertThatThrownBy(() -> verifier.verificar(token))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "TOKEN_GOOGLE_INVALIDO");
    }

    @Test
    void audienciaDeOtraAplicacionSeRechaza() throws Exception {
        String token = firmar(claimsValidos().audience("otra-app").build(), llave);

        assertThatThrownBy(() -> verifier.verificar(token))
                .hasFieldOrPropertyWithValue("code", "TOKEN_GOOGLE_INVALIDO");
    }

    @Test
    void emisorDistintoDeGoogleSeRechaza() throws Exception {
        String token = firmar(claimsValidos().issuer("https://evil.example").build(), llave);

        assertThatThrownBy(() -> verifier.verificar(token))
                .hasFieldOrPropertyWithValue("code", "TOKEN_GOOGLE_INVALIDO");
    }

    @Test
    void tokenVencidoSeRechaza() throws Exception {
        String token = firmar(claimsValidos()
                .expirationTime(Date.from(Instant.now().minusSeconds(3600))).build(), llave);

        assertThatThrownBy(() -> verifier.verificar(token))
                .hasFieldOrPropertyWithValue("code", "TOKEN_GOOGLE_INVALIDO");
    }

    @Test
    void correoSinVerificarSeRechaza() throws Exception {
        String token = firmar(claimsValidos().claim("email_verified", false).build(), llave);

        assertThatThrownBy(() -> verifier.verificar(token))
                .hasFieldOrPropertyWithValue("code", "CORREO_NO_VERIFICADO");
    }

    @Test
    void textoQueNoEsUnJwtSeRechaza() {
        assertThatThrownBy(() -> verifier.verificar("esto-no-es-un-jwt"))
                .hasFieldOrPropertyWithValue("code", "TOKEN_GOOGLE_INVALIDO");
    }
}
