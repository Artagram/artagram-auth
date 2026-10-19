package com.artagram.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RsaJwtServiceTest {

    @Test
    void generaYValidaTokenRs256YPublicaJwks() {
        RsaJwtProperties props = new RsaJwtProperties();
        props.setKeyId("artagram-key-test");
        props.setIssuer("artagram-auth");
        props.setAccessTokenMinutes(60);
        props.setPrivateKeyPath("");
        props.setPublicKeyPath("");

        RsaJwtService service = new RsaJwtService(props);

        UUID id = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000002");
        Usuario usuario = new Usuario(id, "sam.rivera@correo.com", "sam_pixel", "hash");
        usuario.marcarVerificado();
        usuario.setPremium(true);

        String token = service.generarToken(usuario);
        assertThat(token).isNotBlank();

        Optional<RsaJwtService.TokenPrincipal> principal = service.validar(token);
        assertThat(principal).isPresent();
        assertThat(principal.get().usuarioId()).isEqualTo(id);
        assertThat(principal.get().nombreArtista()).isEqualTo("sam_pixel");
        assertThat(principal.get().rolGlobal()).isEqualTo("USUARIO");
        assertThat(principal.get().premium()).isTrue();

        Map<String, Object> jwks = service.obtenerJwks();
        assertThat(jwks).containsKey("keys");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst()).containsEntry("kid", "artagram-key-test");
        assertThat(keys.getFirst()).containsEntry("alg", "RS256");
        assertThat(keys.getFirst()).containsEntry("kty", "RSA");
    }
}
