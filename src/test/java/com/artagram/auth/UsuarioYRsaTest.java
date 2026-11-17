package com.artagram.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsuarioYRsaTest {

    @TempDir
    Path dir;

    @Test
    void prePersistCompletaIdYFechas() {
        Usuario vacio = new Usuario();
        vacio.ensureIdAndTimestamps();
        assertThat(vacio.getId()).isNotNull();
        assertThat(vacio.getCreadoEn()).isNotNull();
        assertThat(vacio.getActualizadoEn()).isNotNull();

        Usuario u = new Usuario((UUID) null, "a@b.com", "ana");
        u.setNombreArtista("ana_art");
        assertThat(u.getId()).isNotNull();
        assertThat(u.getNombreArtista()).isEqualTo("ana_art");
        assertThat(u.isVerificado()).isFalse();
    }

    private static String pem(String tipo, byte[] der) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END " + tipo + "-----\n";
    }

    private static KeyPair par() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    @Test
    void cargaElParRsaDesdePemEnLinea() throws Exception {
        KeyPair par = par();
        RsaJwtProperties props = new RsaJwtProperties();
        props.setPrivateKeyPem(pem("PRIVATE KEY", par.getPrivate().getEncoded()));
        props.setPublicKeyPem(pem("PUBLIC KEY", par.getPublic().getEncoded()));

        RsaJwtService service = new RsaJwtService(props);
        String token = service.generarToken(new Usuario(UUID.randomUUID(), "a@b.com", "ana"));

        assertThat(service.validar(token)).isPresent();
        String n = (String) ((java.util.Map<?, ?>) ((java.util.List<?>) service.obtenerJwks().get("keys")).get(0)).get("n");
        assertThat(Base64.getUrlDecoder().decode(n)).hasSize(256);
    }

    @Test
    void cargaElParRsaDesdeArchivos() throws Exception {
        KeyPair par = par();
        Path priv = Files.writeString(dir.resolve("priv.pem"), pem("PRIVATE KEY", par.getPrivate().getEncoded()));
        Path pub = Files.writeString(dir.resolve("pub.pem"), pem("PUBLIC KEY", par.getPublic().getEncoded()));
        RsaJwtProperties props = new RsaJwtProperties();
        props.setPrivateKeyPath(priv.toString());
        props.setPublicKeyPath(pub.toString());

        RsaJwtService service = new RsaJwtService(props);

        assertThat(service.validar(service.generarToken(new Usuario(UUID.randomUUID(), "a@b.com", "ana")))).isPresent();
    }

    @Test
    void conPemInvalidoGeneraUnParEfimeroYRechazaTokensAjenos() {
        RsaJwtProperties props = new RsaJwtProperties();
        props.setPrivateKeyPem("-----BEGIN PRIVATE KEY-----\nno-es-base64!\n-----END PRIVATE KEY-----");
        props.setPublicKeyPem("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----");
        RsaJwtService service = new RsaJwtService(props);

        RsaJwtProperties otrasProps = new RsaJwtProperties();
        otrasProps.setPrivateKeyPath(dir.resolve("no-existe.pem").toString());
        otrasProps.setPublicKeyPath("");
        RsaJwtService otro = new RsaJwtService(otrasProps);

        String tokenAjeno = otro.generarToken(new Usuario(UUID.randomUUID(), "a@b.com", "ana"));
        assertThat(service.validar(tokenAjeno)).isEmpty();
        assertThat(service.validar("basura")).isEmpty();
    }
}
