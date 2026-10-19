package com.artagram.auth.dto;

import java.util.List;

public record NombreDisponibleResponse(boolean disponible, List<String> sugerencias) {
}
