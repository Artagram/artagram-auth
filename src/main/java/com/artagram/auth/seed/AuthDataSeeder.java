package com.artagram.auth.seed;

import com.artagram.auth.AuthEventPublisher;
import com.artagram.auth.RolGlobal;
import com.artagram.auth.Usuario;
import com.artagram.auth.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeder determinista para artagram-auth con los 12 UUIDs fijos de demostración.
 */
@Component
@Profile("seed")
public class AuthDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthDataSeeder.class);
    private static final String PASSWORD_DEMO = "clavesegura123";

    private record UsuarioSeed(UUID id, String correo, String nombreArtista, RolGlobal rol, boolean premium) {
    }

    private static final List<UsuarioSeed> USUARIOS_DEMO = List.of(
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001"), "lucia.mora@correo.com", "lucia_acuarela", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0000-4000-8000-000000000002"), "sam.rivera@correo.com", "sam_pixel", RolGlobal.USUARIO, true),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0000-4000-8000-000000000003"), "noa.delgado@correo.com", "noa_tinta", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0000-4000-8000-000000000004"), "ivan.rueda@correo.com", "ivan_urbano", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0000-4000-8000-000000000005"), "maria.paz@correo.com", "maria_paz_art", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0000-4000-8000-000000000006"), "diego.leon@correo.com", "diego_grabado", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0000-4000-8000-000000000007"), "prueba.artagram@correo.com", "pincel_artagram", RolGlobal.ADMIN, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0001-4000-8000-000000000001"), "camila.oleo@correo.com", "camila_oleo", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0002-4000-8000-000000000002"), "mateo.3d@correo.com", "mateo_3d", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0003-4000-8000-000000000003"), "valeria.botanica@correo.com", "valeria_botanica", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0004-4000-8000-000000000004"), "andres.concept@correo.com", "andres_concept", RolGlobal.USUARIO, false),
            new UsuarioSeed(UUID.fromString("a1b2c3d4-0005-4000-8000-000000000005"), "elena.collage@correo.com", "elena_collage", RolGlobal.USUARIO, false)
    );

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher eventPublisher;

    public AuthDataSeeder(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          AuthEventPublisher eventPublisher) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run(String... args) {
        String hash = passwordEncoder.encode(PASSWORD_DEMO);
        int creados = 0;
        for (UsuarioSeed seed : USUARIOS_DEMO) {
            if (!usuarioRepository.existsByCorreoIgnoreCase(seed.correo())) {
                Usuario u = new Usuario(seed.id(), seed.correo(), seed.nombreArtista(), hash);
                u.marcarVerificado();
                u.setRolGlobal(seed.rol());
                u.setPremium(seed.premium());
                usuarioRepository.save(u);
                eventPublisher.publicarUsuarioRegistrado(u);
                creados++;
            }
        }
        log.info("AuthDataSeeder completado: {} usuarios nuevos creados (total en BD={})", creados, usuarioRepository.count());
    }
}
