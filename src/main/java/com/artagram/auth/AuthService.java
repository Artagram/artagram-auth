package com.artagram.auth;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.dto.GoogleLoginResponse;
import com.artagram.auth.dto.LoginResponse;
import com.artagram.auth.dto.NombreDisponibleResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int NOMBRE_MIN = 3;
    private static final int NOMBRE_MAX = 30;

    private final UsuarioRepository usuarioRepository;
    private final RsaJwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleProperties googleProperties;
    private final NombreSugeridoGenerator nombreSugeridoGenerator;
    private final AuthEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public AuthService(UsuarioRepository usuarioRepository,
                       RsaJwtService jwtService,
                       GoogleTokenVerifier googleTokenVerifier,
                       GoogleProperties googleProperties,
                       NombreSugeridoGenerator nombreSugeridoGenerator,
                       AuthEventPublisher eventPublisher,
                       MeterRegistry meterRegistry) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.googleProperties = googleProperties;
        this.nombreSugeridoGenerator = nombreSugeridoGenerator;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Ingresa con un ID token de Google. Busca la cuenta por el identificador
     * de Google y, si no existe, por correo: asi los usuarios anteriores al
     * login con Google conservan su perfil. Si tampoco existe, el cliente debe
     * completar el registro eligiendo un nombre de artista.
     */
    @Transactional
    public GoogleLoginResponse ingresarConGoogle(String idToken) {
        GoogleIdentidad identidad = googleTokenVerifier.verificar(idToken);
        return buscarCuenta(identidad)
                .map(usuario -> GoogleLoginResponse.conSesion(iniciarSesion(usuario)))
                .orElseGet(() -> GoogleLoginResponse.faltaNombre(sugerirNombre(identidad)));
    }

    @Transactional
    public LoginResponse completarRegistroGoogle(String idToken, String nombreArtista) {
        GoogleIdentidad identidad = googleTokenVerifier.verificar(idToken);
        if (buscarCuenta(identidad).isPresent()) {
            throw ApiException.conflict("CUENTA_YA_EXISTE", "Esta cuenta de Google ya esta registrada");
        }
        if (usuarioRepository.existsByNombreArtistaIgnoreCase(nombreArtista)) {
            throw ApiException.conflict("NOMBRE_OCUPADO", "Ese nombre de artista ya lo tiene otra persona");
        }

        Usuario usuario = new Usuario(identidad.correo().toLowerCase(Locale.ROOT), nombreArtista, identidad.sub());
        aplicarRolAdmin(usuario);
        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("REGISTRO_DUPLICADO", "Ese correo o nombre de artista ya esta en uso");
        }

        eventPublisher.publicarUsuarioRegistrado(usuario);
        meterRegistry.counter("negocio_eventos_total", "tipo", "REGISTRO").increment();
        log.info("Usuario registrado con Google: id={}", usuario.getId());
        return iniciarSesion(usuario);
    }

    @Transactional(readOnly = true)
    public LoginResponse refrescarToken(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> ApiException.unauthorized("NO_AUTENTICADO", "Debes iniciar sesión para continuar"));
        return new LoginResponse(jwtService.generarToken(usuario), usuario.getId(), usuario.getNombreArtista(),
                usuario.getRolGlobal().name(), usuario.isPremium());
    }

    private Optional<Usuario> buscarCuenta(GoogleIdentidad identidad) {
        Optional<Usuario> porGoogle = usuarioRepository.findByGoogleSub(identidad.sub());
        if (porGoogle.isPresent()) {
            return porGoogle;
        }
        Optional<Usuario> porCorreo = usuarioRepository.findByCorreoIgnoreCase(identidad.correo());
        porCorreo.ifPresent(usuario -> {
            if (usuario.getGoogleSub() != null) {
                throw ApiException.conflict("CUENTA_VINCULADA", "Este correo ya esta vinculado a otra cuenta de Google");
            }
            usuario.vincularGoogle(identidad.sub());
            log.info("Cuenta existente vinculada a Google: id={}", usuario.getId());
        });
        return porCorreo;
    }

    private LoginResponse iniciarSesion(Usuario usuario) {
        aplicarRolAdmin(usuario);
        String token = jwtService.generarToken(usuario);
        meterRegistry.counter("negocio_eventos_total", "tipo", "LOGIN").increment();
        eventPublisher.publicarLogin(usuario);
        return new LoginResponse(token, usuario.getId(), usuario.getNombreArtista(),
                usuario.getRolGlobal().name(), usuario.isPremium());
    }

    private void aplicarRolAdmin(Usuario usuario) {
        if (googleProperties.correosAdmin().contains(usuario.getCorreo().toLowerCase(Locale.ROOT))) {
            usuario.setRolGlobal(RolGlobal.ADMIN);
        }
    }

    private String sugerirNombre(GoogleIdentidad identidad) {
        String fuente = identidad.nombre() != null ? identidad.nombre() : identidad.correo().split("@")[0];
        String base = fuente.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.length() < NOMBRE_MIN) {
            base = "artista";
        }
        base = base.substring(0, Math.min(base.length(), NOMBRE_MAX));
        if (!usuarioRepository.existsByNombreArtistaIgnoreCase(base)) {
            return base;
        }
        List<String> sugerencias = nombreSugeridoGenerator.sugerir(
                base, n -> !usuarioRepository.existsByNombreArtistaIgnoreCase(n), 1);
        return sugerencias.isEmpty() ? "" : sugerencias.get(0);
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
}
