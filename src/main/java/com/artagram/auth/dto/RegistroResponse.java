package com.artagram.auth.dto;

import java.util.UUID;

public record RegistroResponse(
        UUID id,
        String correo,
        String nombreArtista,
        String mensaje,
        boolean verificacionPendiente
) {
}
