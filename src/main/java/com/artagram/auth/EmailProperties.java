package com.artagram.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private String mode = "log";
    private String from;
    private String verificationLinkBase;
    private boolean verificacionRequerida = false;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getVerificationLinkBase() {
        return verificationLinkBase;
    }

    public void setVerificationLinkBase(String verificationLinkBase) {
        this.verificationLinkBase = verificationLinkBase;
    }

    public boolean isVerificacionRequerida() {
        return verificacionRequerida;
    }

    public void setVerificacionRequerida(boolean verificacionRequerida) {
        this.verificacionRequerida = verificacionRequerida;
    }
}
