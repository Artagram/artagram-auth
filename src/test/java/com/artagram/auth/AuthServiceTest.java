package com.artagram.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.dto.GoogleLoginResponse;
import com.artagram.auth.dto.LoginResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final GoogleIdentidad IDENTIDAD =
            new GoogleIdentidad("google-123", "Lucia.Mora@gmail.com", "Lucía Mora");

    private UsuarioRepository usuarioRepository;
    private RsaJwtService jwtService;
    private GoogleTokenVerifier googleTokenVerifier;
    private GoogleProperties googleProperties;
    private AuthEventPublisher eventPublisher;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        RsaJwtProperties jwtProps = new RsaJwtProperties();
        jwtProps.setPrivateKeyPath("");
        jwtProps.setPublicKeyPath("");
        jwtService = new RsaJwtService(jwtProps);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);
        googleProperties = new GoogleProperties();
        eventPublisher = mock(AuthEventPublisher.class);
        when(googleTokenVerifier.verificar("token")).thenReturn(IDENTIDAD);

        authService = new AuthService(usuarioRepository, jwtService, googleTokenVerifier, googleProperties,
                new NombreSugeridoGenerator(), eventPublisher, new SimpleMeterRegistry());
    }

    @Test
    void usuarioConGoogleVinculadoObtieneSesion() {
        Usuario usuario = new Usuario("lucia.mora@gmail.com", "lucia_acuarela", "google-123");
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.of(usuario));

        GoogleLoginResponse respuesta = authService.ingresarConGoogle("token");

        assertThat(respuesta.requiereNombre()).isFalse();
        assertThat(respuesta.sesion().nombreArtista()).isEqualTo("lucia_acuarela");
        assertThat(jwtService.validar(respuesta.sesion().accessToken())).isPresent();
        verify(eventPublisher).publicarLogin(usuario);
    }

    @Test
    void usuarioAnteriorSeVinculaPorCorreoYConservaSuCuenta() {
        UUID id = UUID.randomUUID();
        Usuario anterior = new Usuario(id, "lucia.mora@gmail.com", "lucia_acuarela");
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreoIgnoreCase("Lucia.Mora@gmail.com")).thenReturn(Optional.of(anterior));

        GoogleLoginResponse respuesta = authService.ingresarConGoogle("token");

        assertThat(respuesta.sesion().usuarioId()).isEqualTo(id);
        assertThat(anterior.getGoogleSub()).isEqualTo("google-123");
        assertThat(anterior.isVerificado()).isTrue();
    }

    @Test
    void correoVinculadoAOtraCuentaDeGoogleSeRechaza() {
        Usuario otro = new Usuario("lucia.mora@gmail.com", "lucia_acuarela", "google-999");
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreoIgnoreCase("Lucia.Mora@gmail.com")).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> authService.ingresarConGoogle("token"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CUENTA_VINCULADA");
    }

    @Test
    void cuentaNuevaPideNombreConUnaSugerenciaValida() {
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());

        GoogleLoginResponse respuesta = authService.ingresarConGoogle("token");

        assertThat(respuesta.requiereNombre()).isTrue();
        assertThat(respuesta.sesion()).isNull();
        assertThat(respuesta.nombreSugerido()).matches("^[a-zA-Z0-9_]{3,30}$");
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void completarRegistroCreaLaCuentaYPublicaElEvento() {
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse sesion = authService.completarRegistroGoogle("token", "lucia_nueva");

        assertThat(sesion.nombreArtista()).isEqualTo("lucia_nueva");
        assertThat(sesion.rolGlobal()).isEqualTo("USUARIO");
        verify(eventPublisher).publicarUsuarioRegistrado(any(Usuario.class));
    }

    @Test
    void completarRegistroConNombreOcupadoSeRechaza() {
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.existsByNombreArtistaIgnoreCase("ocupado")).thenReturn(true);

        assertThatThrownBy(() -> authService.completarRegistroGoogle("token", "ocupado"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "NOMBRE_OCUPADO");
    }

    @Test
    void correoDeAdminRecibeElRolAlIngresar() {
        googleProperties.setAdminEmails(" otro@gmail.com , LUCIA.MORA@gmail.com ");
        Usuario usuario = new Usuario("lucia.mora@gmail.com", "lucia_acuarela", "google-123");
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.of(usuario));

        GoogleLoginResponse respuesta = authService.ingresarConGoogle("token");

        assertThat(respuesta.sesion().rolGlobal()).isEqualTo("ADMIN");
    }

    @Test
    void refrescarEmiteUnTokenConElPremiumActualSinContarComoLogin() {
        Usuario usuario = new Usuario("lucia.mora@gmail.com", "lucia_acuarela", "google-123");
        usuario.setPremium(true);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        LoginResponse sesion = authService.refrescarToken(usuario.getId());

        assertThat(sesion.premium()).isTrue();
        assertThat(jwtService.validar(sesion.accessToken()).orElseThrow().premium()).isTrue();
        verify(eventPublisher, never()).publicarLogin(any());
    }

    @Test
    void refrescarUnUsuarioBorradoSeRechaza() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refrescarToken(id))
                .hasFieldOrPropertyWithValue("code", "NO_AUTENTICADO");
    }

    @Test
    void sinCorreosAdminConfiguradosNadieEsAdmin() {
        googleProperties.setAdminEmails("");
        Usuario usuario = new Usuario("lucia.mora@gmail.com", "lucia_acuarela", "google-123");
        when(usuarioRepository.findByGoogleSub("google-123")).thenReturn(Optional.of(usuario));

        assertThat(authService.ingresarConGoogle("token").sesion().rolGlobal()).isEqualTo("USUARIO");
    }
}
