package com.artagram.auth;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.dto.LoginRequest;
import com.artagram.auth.dto.LoginResponse;
import com.artagram.auth.dto.NombreDisponibleResponse;
import com.artagram.auth.dto.RegistroRequest;
import com.artagram.auth.dto.RegistroResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Autenticación", description = "HU-01: registro, verificación de correo, login y gestión de cuenta")
public class AuthController {

    private final AuthService authService;
    private final RsaJwtService rsaJwtService;

    public AuthController(AuthService authService, RsaJwtService rsaJwtService) {
        this.authService = authService;
        this.rsaJwtService = rsaJwtService;
    }

    @PostMapping("/api/auth/registro")
    public ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }

    @GetMapping("/api/auth/verificar")
    public ResponseEntity<Void> verificar(@RequestParam String token) {
        authService.verificar(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
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
