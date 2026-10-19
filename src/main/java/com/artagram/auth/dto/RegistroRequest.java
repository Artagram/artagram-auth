package com.artagram.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Email(message = "Ese correo no parece valido") String correo,
        @NotBlank @Size(min = 10, message = "La contrasena debe tener al menos 10 caracteres") String password,
        @NotBlank
        @Size(min = 3, max = 30, message = "El nombre de artista debe tener entre 3 y 30 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Solo letras, numeros y guion bajo")
        String nombreArtista
) {
}
