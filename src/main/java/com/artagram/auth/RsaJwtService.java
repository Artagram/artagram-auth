package com.artagram.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Emisor asimétrico RS256 de JWT para ArtaGram y proveedor del conjunto de llaves públicas JWKS.
 */
@Service
public final class RsaJwtService {

    private static final Logger log = LoggerFactory.getLogger(RsaJwtService.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final String issuer;
    private final int accessTokenMinutes;

    public RsaJwtService(RsaJwtProperties properties) {
        this.keyId = properties.getKeyId();
        this.issuer = properties.getIssuer();
        this.accessTokenMinutes = properties.getAccessTokenMinutes();
        KeyPair pair = cargarOGenerarParRsa(properties);
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .subject(usuario.getId().toString())
                .claim("nombreArtista", usuario.getNombreArtista())
                .claim("nombre", usuario.getNombreArtista())
                .claim("rolGlobal", usuario.getRolGlobal().name())
                .claim("rol", usuario.getRolGlobal().name())
                .claim("premium", usuario.isPremium())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(accessTokenMinutes, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Optional<TokenPrincipal> validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID usuarioId = UUID.fromString(claims.getSubject());
            String nombreArtista = claims.get("nombreArtista", String.class);
            String rolGlobal = claims.get("rolGlobal", String.class);
            boolean premium = Boolean.TRUE.equals(claims.get("premium", Boolean.class));
            return Optional.of(new TokenPrincipal(usuarioId, nombreArtista, rolGlobal, premium));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token RS256 inválido o vencido: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, Object> obtenerJwks() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", base64UrlUnsigned(publicKey.getModulus()));
        jwk.put("e", base64UrlUnsigned(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(jwk));
    }

    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair cargarOGenerarParRsa(RsaJwtProperties props) {
        try {
            String privPem = resolverPem(props.getPrivateKeyPem(), props.getPrivateKeyPath());
            String pubPem = resolverPem(props.getPublicKeyPem(), props.getPublicKeyPath());
            if (privPem != null && !privPem.isBlank() && pubPem != null && !pubPem.isBlank()) {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                RSAPrivateKey priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(decodificarPem(privPem)));
                RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(decodificarPem(pubPem)));
                log.info("Par RSA 2048 cargado exitosamente (kid={})", props.getKeyId());
                return new KeyPair(pub, priv);
            }
        } catch (Exception e) {
            log.warn("No se pudo cargar par RSA desde archivos/variables ({}), generando par efímero de desarrollo", e.getMessage());
        }
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            log.info("Par RSA 2048 efímero generado en memoria (kid={})", props.getKeyId());
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar el par de llaves RSA", e);
        }
    }

    private static String resolverPem(String inlinePem, String filePath) throws IOException {
        if (inlinePem != null && !inlinePem.isBlank()) {
            return inlinePem;
        }
        if (filePath != null && !filePath.isBlank()) {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                return Files.readString(path);
            }
        }
        return null;
    }

    private static byte[] decodificarPem(String pem) {
        String limpio = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(limpio);
    }

    public record TokenPrincipal(UUID usuarioId, String nombreArtista, String rolGlobal, boolean premium) {
    }
}
