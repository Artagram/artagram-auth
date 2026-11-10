package com.artagram.auth;

/** Datos de un ID token de Google ya validado. */
public record GoogleIdentidad(String sub, String correo, String nombre) {
}
