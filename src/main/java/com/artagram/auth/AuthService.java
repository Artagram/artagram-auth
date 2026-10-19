package com.artagram.auth;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.dto.LoginRequest;
import com.artagram.auth.dto.LoginResponse;
import com.artagram.auth.dto.NombreDisponibleResponse;
import com.artagram.auth.dto.RegistroRequest;
import com.artagram.auth.dto.RegistroResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFICACION_HORAS = 24;

    private final UsuarioRepository usuarioRepository;
    private final TokenVerificacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RsaJwtService jwtService;
    private final EmailService emailService;
    private final EmailProperties emailProperties;
    private final NombreSugeridoGenerator nombreSugeridoGenerator;
    private final AuthEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public AuthService(UsuarioRepository usuarioRepository,
                       TokenVerificacionRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       RsaJwtService jwtService,
                       EmailService emailService,
                       EmailProperties emailProperties,
                       NombreSugeridoGenerator nombreSugeridoGenerator,
                       AuthEventPublisher eventPublisher,
                       MeterRegistry meterRegistry) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
        this.nombreSugeridoGenerator = nombreSugeridoGenerator;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public RegistroResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByCorreoIgnoreCase(request.correo())) {
            throw ApiException.conflict("CORREO_YA_REGISTRADO", "Este correo ya está registrado");
        }
        if (usuarioRepository.existsByNombreArtistaIgnoreCase(request.nombreArtista())) {
            throw ApiException.conflict("NOMBRE_OCUPADO", "Ese nombre de artista ya lo tiene otra persona");
        }

        Usuario usuario = new Usuario(
                request.correo().toLowerCase(),
                request.nombreArtista(),
                passwordEncoder.encode(request.password())
        );
        if (!emailProperties.isVerificacionRequerida()) {
            usuario.marcarVerificado();
        }
        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("REGISTRO_DUPLICADO", "Ese correo o nombre de artista ya esta en uso");
        }

        eventPublisher.publicarUsuarioRegistrado(usuario);
        meterRegistry.counter("negocio_eventos_total", "tipo", "REGISTRO").increment();

        if (!emailProperties.isVerificacionRequerida()) {
            log.info("Usuario registrado y verificado de inmediato (verificacion por correo desactivada): id={}", usuario.getId());
            return new RegistroResponse(usuario.getId(), usuario.getCorreo(), usuario.getNombreArtista(),
                    "Tu cuenta está lista, ya puedes iniciar sesión", false);
        }

        String token = generarTokenAleatorio();
        tokenRepository.save(new TokenVerificacion(usuario, token, Instant.now().plus(VERIFICACION_HORAS, ChronoUnit.HOURS)));

        String enlace = emailProperties.getVerificationLinkBase() + "?token=" + token;
        emailService.enviarVerificacion(usuario.getCorreo(), usuario.getNombreArtista(), enlace);

        log.info("Usuario registrado, pendiente de verificacion: id={}", usuario.getId());
        return new RegistroResponse(usuario.getId(), usuario.getCorreo(), usuario.getNombreArtista(),
                "Revisa tu bandeja de entrada para confirmar tu cuenta", true);
    }

    @Transactional
    public void verificar(String token) {
        TokenVerificacion tokenVerificacion = tokenRepository.findByToken(token)
                .orElseThrow(() -> ApiException.badRequest("TOKEN_INVALIDO", "El enlace de verificacion no es valido"));
        if (tokenVerificacion.estaUsado()) {
            throw ApiException.badRequest("TOKEN_YA_USADO", "Este enlace ya fue usado");
        }
        if (tokenVerificacion.estaExpirado()) {
            throw ApiException.badRequest("TOKEN_EXPIRADO", "El enlace de verificacion vencio, pide uno nuevo");
        }
        tokenVerificacion.marcarUsado();
        tokenVerificacion.getUsuario().marcarVerificado();
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(request.correo())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contrasena incorrectos"));
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contrasena incorrectos");
        }
        if (!usuario.isVerificado()) {
            throw ApiException.forbidden("CUENTA_NO_VERIFICADA", "Verifica tu correo antes de iniciar sesión");
        }
        String token = jwtService.generarToken(usuario);
        meterRegistry.counter("negocio_eventos_total", "tipo", "LOGIN").increment();
        return new LoginResponse(token, usuario.getId(), usuario.getNombreArtista(),
                usuario.getRolGlobal().name(), usuario.isPremium());
    }

    @Transactional(readOnly = true)
    public NombreDisponibleResponse verificarNombreDisponible(String nombre) {
        boolean disponible = !usuarioRepository.existsByNombreArtistaIgnoreCase(nombre);
        List<String> sugerencias = disponible
                ? List.of()
                : nombreSugeridoGenerator.sugerir(nombre, n -> !usuarioRepository.existsByNombreArtistaIgnoreCase(n), 3);
        return new NombreDisponibleResponse(disponible, sugerencias);
    }

    @Transactional
    public void eliminarCuenta(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> ApiException.notFound("USUARIO_NO_ENCONTRADO", "El usuario no existe"));
        String nombreArtista = usuario.getNombreArtista();
        usuarioRepository.delete(usuario);
        eventPublisher.publicarUsuarioEliminado(usuarioId, nombreArtista);
    }

    private String generarTokenAleatorio() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
