package com.artagram.auth;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del ingreso con Google.
 * <p>
 * {@code adminEmails} reemplaza a los administradores sembrados: esas cuentas
 * no son de Google y ya no podrian entrar, asi que el rol ADMIN se otorga al
 * ingresar con alguno de estos correos (lista separada por comas).
 */
@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {

    private String clientId = "";
    private String jwksUri = "https://www.googleapis.com/oauth2/v3/certs";
    private String adminEmails = "";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(String adminEmails) {
        this.adminEmails = adminEmails;
    }

    public Set<String> correosAdmin() {
        return Arrays.stream(adminEmails.split(","))
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
