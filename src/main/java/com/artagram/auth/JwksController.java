package com.artagram.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "JWKS", description = "Llaves públicas RSA para validación de JWT en el Gateway y servicios internos")
public class JwksController {

    private final RsaJwtService rsaJwtService;

    public JwksController(RsaJwtService rsaJwtService) {
        this.rsaJwtService = rsaJwtService;
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Obtiene el JSON Web Key Set (JWKS) con la clave pública RSA activa")
    public Map<String, Object> jwks() {
        return rsaJwtService.obtenerJwks();
    }
}
