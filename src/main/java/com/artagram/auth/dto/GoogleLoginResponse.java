package com.artagram.auth.dto;

/**
 * Resultado de ingresar con Google. Si la cuenta es nueva no hay sesion
 * todavia: {@code requiereNombre} es true y el cliente debe pedir el nombre de
 * artista y llamar a /api/auth/google/completar.
 */
public record GoogleLoginResponse(boolean requiereNombre, String nombreSugerido, LoginResponse sesion) {

    public static GoogleLoginResponse conSesion(LoginResponse sesion) {
        return new GoogleLoginResponse(false, null, sesion);
    }

    public static GoogleLoginResponse faltaNombre(String nombreSugerido) {
        return new GoogleLoginResponse(true, nombreSugerido, null);
    }
}
