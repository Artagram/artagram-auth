package com.artagram.auth;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.dto.GoogleCompletarRequest;
import com.artagram.auth.dto.GoogleLoginRequest;
import com.artagram.auth.dto.GoogleLoginResponse;
import com.artagram.auth.dto.LoginResponse;
import com.artagram.auth.dto.NombreDisponibleResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Autenticación", description = "HU-01: ingreso con Google y gestión de cuenta")
public class AuthController {

    private final AuthService authService;
    private final RsaJwtService rsaJwtService;

    public AuthController(AuthService authService, RsaJwtService rsaJwtService) {
        this.authService = authService;
        this.rsaJwtService = rsaJwtService;
    }

    @PostMapping("/api/auth/google")
    public ResponseEntity<GoogleLoginResponse> ingresarConGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.ingresarConGoogle(request.idToken()));
    }

    @PostMapping("/api/auth/google/completar")
    public ResponseEntity<LoginResponse> completarRegistro(@Valid @RequestBody GoogleCompletarRequest request) {
        return ResponseEntity.ok(authService.completarRegistroGoogle(request.idToken(), request.nombreArtista()));
    }

    @GetMapping("/api/auth/nombre-disponible")
    public ResponseEntity<NombreDisponibleResponse> nombreDisponible(@RequestParam String nombre) {
        return ResponseEntity.ok(authService.verificarNombreDisponible(nombre));
    }

    @DeleteMapping("/api/auth/cuenta")
    public ResponseEntity<Void> eliminarCuenta(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            HttpServletRequest request) {
        UUID usuarioId = resolverUsuarioAutenticado(xUserId, request);
        authService.eliminarCuenta(usuarioId);
        return ResponseEntity.noContent().build();
    }

    private UUID resolverUsuarioAutenticado(String xUserId, HttpServletRequest request) {
        if (xUserId != null && !xUserId.isBlank()) {
            return UUID.fromString(xUserId);
        }
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return rsaJwtService.validar(authHeader.substring(7).trim())
                    .map(RsaJwtService.TokenPrincipal::usuarioId)
                    .orElseThrow(() -> ApiException.unauthorized("NO_AUTENTICADO", "Debes iniciar sesión para continuar"));
        }
        throw ApiException.unauthorized("NO_AUTENTICADO", "Debes iniciar sesión para continuar");
    }
}
