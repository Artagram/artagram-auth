package com.artagram.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.dto.LoginRequest;
import com.artagram.auth.dto.LoginResponse;
import com.artagram.auth.dto.RegistroRequest;
import com.artagram.auth.dto.RegistroResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UsuarioRepository usuarioRepository;
    private TokenVerificacionRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private RsaJwtService jwtService;
    private EmailService emailService;
    private EmailProperties emailProperties;
    private AuthEventPublisher eventPublisher;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        tokenRepository = mock(TokenVerificacionRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        RsaJwtProperties jwtProps = new RsaJwtProperties();
        jwtProps.setPrivateKeyPath("");
        jwtProps.setPublicKeyPath("");
        jwtService = new RsaJwtService(jwtProps);
        emailService = mock(EmailService.class);
        emailProperties = new EmailProperties();
        emailProperties.setVerificacionRequerida(false);
        eventPublisher = mock(AuthEventPublisher.class);

        authService = new AuthService(
                usuarioRepository,
                tokenRepository,
                passwordEncoder,
                jwtService,
                emailService,
                emailProperties,
                new NombreSugeridoGenerator(),
                eventPublisher,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void registraUsuarioYPublicaEventoEnRabbitMq() {
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@correo.com")).thenReturn(false);
        when(usuarioRepository.existsByNombreArtistaIgnoreCase("nuevo_artista")).thenReturn(false);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroResponse resp = authService.registrar(
                new RegistroRequest("nuevo@correo.com", "clavesegura123", "nuevo_artista"));

        assertThat(resp.nombreArtista()).isEqualTo("nuevo_artista");
        assertThat(resp.verificacionPendiente()).isFalse();
        verify(eventPublisher).publicarUsuarioRegistrado(any(Usuario.class));
    }

    @Test
    void loginExitosoDevuelveTokenRs256() {
        UUID id = UUID.randomUUID();
        Usuario u = new Usuario(id, "lucia.mora@correo.com", "lucia_acuarela", passwordEncoder.encode("clavesegura123"));
        u.marcarVerificado();
        when(usuarioRepository.findByCorreoIgnoreCase("lucia.mora@correo.com")).thenReturn(Optional.of(u));

        LoginResponse resp = authService.login(new LoginRequest("lucia.mora@correo.com", "clavesegura123"));

        assertThat(resp.usuarioId()).isEqualTo(id);
        assertThat(resp.nombreArtista()).isEqualTo("lucia_acuarela");
        assertThat(jwtService.validar(resp.accessToken())).isPresent();
    }

    @Test
    void loginConClaveIncorrectaLanza401() {
        Usuario u = new Usuario(UUID.randomUUID(), "lucia.mora@correo.com", "lucia_acuarela", passwordEncoder.encode("clavesegura123"));
        u.marcarVerificado();
        when(usuarioRepository.findByCorreoIgnoreCase("lucia.mora@correo.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login(new LoginRequest("lucia.mora@correo.com", "otra_clave")))
                .isInstanceOf(ApiException.class);
    }
}
