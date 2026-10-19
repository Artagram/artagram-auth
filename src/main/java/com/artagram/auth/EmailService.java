package com.artagram.auth;

public interface EmailService {

    void enviarVerificacion(String correoDestino, String nombreArtista, String enlaceVerificacion);
}
