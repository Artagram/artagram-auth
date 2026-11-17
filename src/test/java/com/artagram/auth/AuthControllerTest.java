package com.artagram.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.common.GlobalExceptionHandler;
import com.artagram.auth.dto.GoogleLoginResponse;
import com.artagram.auth.dto.LoginResponse;
import com.artagram.auth.dto.NombreDisponibleResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private final UUID usuarioId = UUID.randomUUID();
    private final LoginResponse sesion = new LoginResponse("jwt", usuarioId, "lucia", "USUARIO", false);

    private AuthService authService;
    private RsaJwtService jwtService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(RsaJwtService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, jwtService), new JwksController(jwtService))
                .setControllerAdvice(new GlobalExceptionHandler(new SimpleMeterRegistry()))
                .build();
    }

    @Test
    void ingresarConGoogleDevuelveLaSesion() throws Exception {
        when(authService.ingresarConGoogle("tok")).thenReturn(GoogleLoginResponse.conSesion(sesion));

        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"tok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereNombre").value(false))
                .andExpect(jsonPath("$.sesion.accessToken").value("jwt"));
    }

    @Test
    void completarRegistroDevuelveLaSesion() throws Exception {
        when(authService.completarRegistroGoogle("tok", "lucia_art")).thenReturn(sesion);

        mvc.perform(post("/api/auth/google/completar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"tok\",\"nombreArtista\":\"lucia_art\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreArtista").value("lucia"));
    }

    @Test
    void completarRegistroConNombreInvalidoDevuelveLasViolaciones() throws Exception {
        mvc.perform(post("/api/auth/google/completar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"tok\",\"nombreArtista\":\"a b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACION"))
                .andExpect(jsonPath("$.violations[0].field").value("nombreArtista"));
    }

    @Test
    void cuerpoQueNoEsJsonDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{no-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUERPO_INVALIDO"));
    }

    @Test
    void refrescarTokenConBearerValido() throws Exception {
        when(jwtService.validar("jwt")).thenReturn(Optional.of(
                new RsaJwtService.TokenPrincipal(usuarioId, "lucia", "USUARIO", false)));
        when(authService.refrescarToken(usuarioId)).thenReturn(sesion);

        mvc.perform(post("/api/auth/token/refrescar").header("Authorization", "Bearer jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"));
    }

    @Test
    void refrescarTokenSinTokenOConTokenInvalidoEs401() throws Exception {
        when(jwtService.validar("malo")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/token/refrescar"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
        mvc.perform(post("/api/auth/token/refrescar").header("Authorization", "Bearer malo"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/token/refrescar").header("Authorization", "Basic xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nombreDisponibleYParametroFaltante() throws Exception {
        when(authService.verificarNombreDisponible("lucia")).thenReturn(new NombreDisponibleResponse(false, List.of("lucia_art")));

        mvc.perform(get("/api/auth/nombre-disponible").param("nombre", "lucia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.sugerencias[0]").value("lucia_art"));
        mvc.perform(get("/api/auth/nombre-disponible"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_FALTANTE"));
    }

    @Test
    void eliminarCuentaConCabeceraDelGatewayOConToken() throws Exception {
        mvc.perform(delete("/api/auth/cuenta").header("X-User-Id", usuarioId.toString()))
                .andExpect(status().isNoContent());
        verify(authService).eliminarCuenta(usuarioId);

        UUID otro = UUID.randomUUID();
        when(jwtService.validar("jwt")).thenReturn(Optional.of(new RsaJwtService.TokenPrincipal(otro, "x", "USUARIO", false)));
        mvc.perform(delete("/api/auth/cuenta").header("X-User-Id", " ").header("Authorization", "Bearer jwt"))
                .andExpect(status().isNoContent());
        verify(authService).eliminarCuenta(otro);
    }

    @Test
    void erroresDeNegocioYErroresInesperados() throws Exception {
        when(authService.verificarNombreDisponible("conflicto"))
                .thenThrow(ApiException.conflict("NOMBRE_OCUPADO", "ocupado"));
        when(authService.verificarNombreDisponible("caida"))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAIDA", "sin base"));
        when(authService.verificarNombreDisponible("boom")).thenThrow(new IllegalStateException("boom"));

        mvc.perform(get("/api/auth/nombre-disponible").param("nombre", "conflicto"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOMBRE_OCUPADO"));
        mvc.perform(get("/api/auth/nombre-disponible").param("nombre", "caida"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CAIDA"));
        mvc.perform(get("/api/auth/nombre-disponible").param("nombre", "boom"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("ERROR_INTERNO"));
    }

    @Test
    void metodoNoPermitidoDevuelve405() throws Exception {
        mvc.perform(put("/api/auth/google"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void jwksExponeLaLlavePublica() throws Exception {
        when(jwtService.obtenerJwks()).thenReturn(Map.of("keys", List.of(Map.of("kid", "k1"))));

        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("k1"));
    }

    @Test
    void googleLoginSinTokenEsInvalido() throws Exception {
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(authService);
    }
}
